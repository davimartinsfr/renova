"""Gera app/src/main/res/raw/step_chime.wav, o toque da troca de etapa dos 7 minutos.

Arpejo suave de Sol, Si e Ré com ataque lento e quase sem harmônicos, como um piano com feltro.
Feito por síntese, sem amostras de terceiros. Uso: python3 tools/step_chime.py
"""
import math
import struct
import wave
from pathlib import Path

SR = 44100
OUT = Path(__file__).resolve().parent.parent / "app/src/main/res/raw/step_chime.wav"


def note(freq, start, tau, attack, harmonic, gain, total):
    """Uma nota: fundamental mais a oitava fraca, com subida em cosseno e decaimento exponencial."""
    out = [0.0] * int(SR * total)
    length = total - start - 0.001
    for i in range(int(SR * length)):
        t = i / SR
        rise = 0.5 - 0.5 * math.cos(math.pi * min(t / attack, 1))
        fade = max(min(1, (length - t) / 0.15), 0)  # termina sem corte
        v = math.exp(-t / tau) * math.sin(2 * math.pi * freq * t)
        v += harmonic * math.exp(-t / (tau * 0.4)) * math.sin(2 * math.pi * 2 * freq * t)
        out[int(SR * start) + i] += gain * rise * fade * v
    return out


def main():
    total = 2.9
    notes = [(392.0, 0.0, 0.6, 0.045, 0.03, 1.0), (493.88, 0.3, 0.65, 0.045, 0.025, 0.85), (587.33, 0.6, 0.85, 0.045, 0.02, 0.75)]
    mix = [sum(s) for s in zip(*(note(*n, total) for n in notes))]
    gain = 10 ** (-12 / 20) / max(abs(x) for x in mix)  # pico em -12 dBFS
    with wave.open(str(OUT), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, x * gain)) * 32767)) for x in mix))


if __name__ == "__main__":
    main()
