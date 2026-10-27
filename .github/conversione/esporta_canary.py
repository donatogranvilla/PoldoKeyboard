# Converte un modello NVIDIA Canary (per esempio nvidia/canary-1b-v2) nei file ONNX int8 che sherpa-onnx carica con
# OfflineCanaryModel: encoder, decoder e tokens.txt con i metadati che il motore legge.
#
# Deriva da scripts/nemo/canary/export_onnx_180m_flash.py di sherpa-onnx (Copyright 2025 Xiaomi Corp., Apache-2.0),
# reso indipendente dal modello: il numero di strati del decoder e la loro dimensione si leggono dal modello invece
# di essere fissati a quelli di canary-180m-flash.
#
# Uso: python esporta_canary.py nvidia/canary-1b-v2 CARTELLA_USCITA
import os
import sys
from pathlib import Path

import onnx
import torch
from onnxruntime.quantization import QuantType, quantize_dynamic

import nemo
from nemo.collections.common.parts import NEG_INF


def maschera_attenzione(input_mask: torch.Tensor, diagonal=None):
    """La maschera di NeMo riscritta senza operazioni che l'esportazione ONNX non regge (come nello script di
    sherpa-onnx): 0 dove l'attenzione è permessa, NEG_INF altrove."""
    if input_mask is None:
        return None
    attn_shape = (1, input_mask.shape[1], input_mask.shape[1])
    attn_mask = input_mask.to(dtype=bool).unsqueeze(1)
    if diagonal is not None:
        future_mask = torch.tril(torch.ones(attn_shape, dtype=torch.int64, device=input_mask.device), diagonal).bool()
        attn_mask = attn_mask & future_mask
    attention_mask = (1 - attn_mask.to(torch.float)) * NEG_INF
    return attention_mask.unsqueeze(1)


nemo.collections.common.parts.form_attention_mask = maschera_attenzione

from nemo.collections.asr.models import EncDecMultiTaskModel  # noqa: E402  dopo la sostituzione della maschera


def aggiungi_metadati(file: Path, metadati: dict):
    modello = onnx.load(str(file))
    while len(modello.metadata_props):
        modello.metadata_props.pop()
    for chiave, valore in metadati.items():
        voce = modello.metadata_props.add()
        voce.key = chiave
        voce.value = str(valore)
    onnx.save(modello, str(file))


def lunghezze_in_maschera(lunghezze, massimo):
    return torch.arange(massimo, device=lunghezze.device).expand(lunghezze.shape[0], massimo) < lunghezze.unsqueeze(1)


class Encoder(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.encoder = m.encoder
        self.encoder_decoder_proj = m.encoder_decoder_proj

    def forward(self, x: torch.Tensor, x_len: torch.Tensor):
        """x: (N, T, C) caratteristiche mel; restituisce stati (N, T', D), lunghezze e maschera."""
        codificato, lunghezze = self.encoder(audio_signal=x.permute(0, 2, 1), length=x_len)
        stati = self.encoder_decoder_proj(codificato.permute(0, 2, 1))
        return stati, lunghezze, lunghezze_in_maschera(lunghezze, stati.shape[1])


def classe_decoder(numero_memorie: int):
    """Il decoder con un argomento per ogni memoria degli strati: l'esportazione vuole argomenti con nome, e il loro
    numero dipende dal modello, perciò la classe si genera con quel numero."""
    memorie = [f'decoder_mems_list_{i}' for i in range(numero_memorie)]
    codice = f'''
class Decoder(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.decoder = m.transf_decoder
        self.log_softmax = m.log_softmax
        # Solo ricerca greedy: il log_softmax non serve
        self.log_softmax.mlp.log_softmax = False

    def forward(self, decoder_input_ids, {", ".join(memorie)}, enc_states, enc_mask):
        pos = decoder_input_ids[0][-1].item()
        decoder_input_ids = decoder_input_ids[:, :-1]
        stati = self.decoder.embedding.forward(decoder_input_ids, start_pos=pos)
        maschera = torch.ones_like(decoder_input_ids).float()
        memorie = self.decoder.decoder.forward(
            stati, maschera, enc_states, enc_mask, [{", ".join(memorie)}], return_mems=True)
        return self.log_softmax(hidden_states=memorie[-1][:, -1:]), memorie
'''
    spazio = {'torch': torch}
    exec(codice, spazio)
    return spazio['Decoder'], memorie


def esporta_encoder(modello, uscita: Path):
    x = torch.rand(1, 4000, 128)
    torch.onnx.export(
        Encoder(modello), (x, torch.tensor([x.shape[1]], dtype=torch.int64)), str(uscita / 'encoder.onnx'),
        input_names=['x', 'x_len'], output_names=['enc_states', 'enc_len', 'enc_mask'], opset_version=14,
        dynamic_axes={'x': {0: 'N', 1: 'T'}, 'x_len': {0: 'N'}, 'enc_states': {0: 'N', 1: 'T'},
                      'enc_len': {0: 'N'}, 'enc_mask': {0: 'N', 1: 'T'}},
    )


def esporta_decoder(modello, uscita: Path, numero_memorie: int, dimensione: int):
    Decoder, memorie = classe_decoder(numero_memorie)
    argomenti = (
        torch.tensor([[1, 0]], dtype=torch.int32),
        *[torch.zeros(1, 10, dimensione) for _ in memorie],
        torch.zeros(1, 1000, dimensione),
        torch.ones(1, 1000).bool(),
    )
    torch.onnx.export(
        Decoder(modello), argomenti, str(uscita / 'decoder.onnx'), dynamo=True, opset_version=14,
        external_data=False,
        input_names=['decoder_input_ids', *memorie, 'enc_states', 'enc_mask'],
        output_names=['logits', *[f'next_decoder_mem_list_{i}' for i in range(numero_memorie)]],
        dynamic_axes={'decoder_input_ids': {1: 'num_tokens'}, **{m: {1: 'num_tokens'} for m in memorie},
                      'enc_states': {1: 'T'}, 'enc_mask': {1: 'T'}},
    )


def esporta_token(modello, uscita: Path):
    with open(uscita / 'tokens.txt', 'w', encoding='utf-8') as file:
        for i in range(modello.tokenizer.vocab_size):
            testo = modello.tokenizer.ids_to_text([i])
            if testo.startswith(' '):
                testo = '▁' + testo[1:]
            file.write(f'{testo} {i}\n')


@torch.no_grad()
def principale():
    nome, uscita = sys.argv[1], Path(sys.argv[2])
    uscita.mkdir(parents=True, exist_ok=True)
    modello = EncDecMultiTaskModel.from_pretrained(nome)
    modello.eval()

    pre = modello.cfg['preprocessor']
    # Le caratteristiche che sherpa-onnx calcola per Canary: le stesse del modello, o il testo esce sbagliato
    assert pre['sample_rate'] == 16000 and pre['normalize'] == 'per_feature' and pre['window'] == 'hann'
    assert pre['window_size'] == 0.025 and pre['window_stride'] == 0.01 and pre['n_fft'] == 512
    assert pre['features'] == 128 and modello.cfg['encoder']['subsampling_factor'] == 8

    # Le memorie sono l'ingresso del primo strato più l'uscita di ogni strato
    numero_memorie = len(modello.transf_decoder.decoder.layers) + 1
    dimensione = modello.transf_decoder.embedding.token_embedding.embedding_dim
    print(f'{nome}: {numero_memorie} memorie del decoder da {dimensione}', flush=True)

    esporta_token(modello, uscita)
    esporta_encoder(modello, uscita)
    esporta_decoder(modello, uscita, numero_memorie, dimensione)

    for parte in ('encoder', 'decoder'):
        quantize_dynamic(model_input=str(uscita / f'{parte}.onnx'), model_output=str(uscita / f'{parte}.int8.onnx'),
                         weight_type=QuantType.QUInt8)

    metadati = {
        'vocab_size': modello.tokenizer.vocab_size,
        'normalize_type': pre['normalize'],
        'subsampling_factor': 8,
        'model_type': 'EncDecMultiTaskModel',
        'version': '1',
        'model_author': 'NeMo',
        'url': f'https://huggingface.co/{nome}',
        'feat_dim': 128,
        'num_decoder_layers': numero_memorie,
        'decoder_hidden_size': dimensione,
    }
    for file in ('encoder.onnx', 'encoder.int8.onnx'):
        aggiungi_metadati(uscita / file, metadati)
    # onnxruntime 1.17 legge al massimo la versione 9 del formato
    for file in ('decoder.onnx', 'decoder.int8.onnx'):
        modello_onnx = onnx.load(str(uscita / file))
        modello_onnx.ir_version = 9
        onnx.save(modello_onnx, str(uscita / file))
    os.system(f'ls -lh {uscita}')


principale()
