#!/usr/bin/env python3
"""Génère les bruitages (assets/sfx/*.wav) et la musique (assets/music/theme.mp3) par synthèse.

Tout est créé ici (aucun son tiers) : formes d'onde simples façon chiptune.
Usage : python3 tools/gen_audio.py   (nécessite numpy, et ffmpeg pour la musique en mp3)
"""
import os
import subprocess
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SFX = os.path.join(ROOT, "assets", "sfx")
MUSIC = os.path.join(ROOT, "assets", "music")
RATE = 22050


def t(dur):
    return np.arange(int(RATE * dur)) / RATE


def env(n, attack=0.005, release=0.6):
    e = np.ones(n)
    a = max(1, int(RATE * attack))
    e[:a] = np.linspace(0, 1, a)
    r = int(n * release)
    if r > 0:
        e[-r:] *= np.linspace(1, 0, r) ** 2
    return e


def square(freq, dur, duty=0.5):
    x = t(dur)
    return np.where((x * freq) % 1 < duty, 1.0, -1.0)


def sine(freq, dur):
    return np.sin(2 * np.pi * freq * t(dur))


def tri(freq, dur):
    x = (t(dur) * freq) % 1
    return 4 * np.abs(x - 0.5) - 1


def noise(dur):
    return np.random.default_rng(7).uniform(-1, 1, int(RATE * dur))


def sweep(f0, f1, dur, wave_fn="square"):
    x = t(dur)
    f = np.linspace(f0, f1, len(x))
    phase = np.cumsum(f) / RATE
    if wave_fn == "square":
        return np.where(phase % 1 < 0.5, 1.0, -1.0)
    return np.sin(2 * np.pi * phase)


def notes(seq, dur, fn=square, gap=0.0, vol=1.0):
    parts = []
    for f in seq:
        s = fn(f, dur) * env(int(RATE * dur), release=0.5) * vol if f else np.zeros(int(RATE * dur))
        parts.append(s)
        if gap:
            parts.append(np.zeros(int(RATE * gap)))
    return np.concatenate(parts)


def save(path, data, vol=0.5):
    data = np.clip(data * vol, -1, 1)
    pcm = (data * 32767).astype(np.int16)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm.tobytes())


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def gen_sfx():
    n = noise(0.09)
    save(os.path.join(SFX, "hit.wav"), (n * 0.6 + sine(110, 0.09) * 0.8) * env(len(n), release=0.9), 0.45)
    n = noise(0.14)
    save(os.path.join(SFX, "crit.wav"), (n * 0.8 + square(90, 0.14) * 0.5) * env(len(n), release=0.8), 0.5)
    s = sweep(300, 1200, 0.32) * env(int(RATE * 0.32), release=0.4)
    save(os.path.join(SFX, "ability.wav"), s, 0.25)
    save(os.path.join(SFX, "buy.wav"), notes([988, 1319], 0.06, sine), 0.5)
    save(os.path.join(SFX, "merge.wav"), notes([midi(72), midi(76), midi(79), midi(84)], 0.07, square, vol=0.7), 0.3)
    save(os.path.join(SFX, "levelup.wav"), notes([midi(67), midi(72), midi(76), midi(79), midi(84)], 0.08, square, vol=0.7), 0.3)
    save(os.path.join(SFX, "win.wav"), notes([midi(72), midi(76), midi(79), 0, midi(84)], 0.11, square, vol=0.7), 0.3)
    save(os.path.join(SFX, "lose.wav"), notes([midi(67), midi(63), midi(60), midi(55)], 0.14, tri), 0.45)
    save(os.path.join(SFX, "click.wav"), sine(1800, 0.025) * env(int(RATE * 0.025), release=0.9), 0.35)
    s = sweep(700, 120, 0.3, "sine") * env(int(RATE * 0.3), release=0.5)
    save(os.path.join(SFX, "faint.wav"), s, 0.4)
    print("bruitages : ok")


def gen_music():
    """Boucle de 8 mesures à 132 bpm : basse triangle, arpèges carrés, mélodie carrée 25 %."""
    bpm = 132
    beat = 60 / bpm
    step = beat / 4  # double-croche
    # Progression : C - Am - F - G (x2), en notes MIDI
    chords = [(48, [60, 64, 67]), (45, [57, 60, 64]), (41, [57, 60, 65]), (43, [55, 59, 62])] * 2
    melody = [76, 0, 79, 76, 74, 0, 72, 0, 72, 74, 76, 0, 74, 72, 69, 0,
              72, 0, 69, 72, 77, 76, 74, 0, 74, 0, 71, 74, 79, 0, 77, 74,
              76, 0, 79, 81, 79, 0, 76, 0, 72, 74, 76, 0, 77, 76, 74, 0,
              72, 0, 69, 72, 77, 76, 74, 72, 71, 0, 74, 0, 72, 0, 0, 0]
    total = int(RATE * step * 16 * len(chords))
    out = np.zeros(total)
    for bar, (bass, triad) in enumerate(chords):
        for k in range(16):
            i = int(RATE * step * (bar * 16 + k))
            # basse sur les temps
            if k % 4 == 0:
                s = tri(midi(bass), beat * 0.9) * env(int(RATE * beat * 0.9), release=0.3) * 0.5
                out[i:i + len(s)] += s[:total - i]
            # arpège en double-croches
            f = midi(triad[k % 3] + 12)
            s = square(f, step * 0.9, 0.5) * env(int(RATE * step * 0.9), release=0.7) * 0.10
            out[i:i + len(s)] += s[:total - i]
            # mélodie en croches
            if k % 2 == 0:
                m = melody[(bar * 8 + k // 2) % len(melody)]
                if m:
                    s = square(midi(m), step * 1.8, 0.25) * env(int(RATE * step * 1.8), release=0.4) * 0.16
                    out[i:i + len(s)] += s[:total - i]
            # charleston (bruit court)
            if k % 2 == 1:
                s = noise(0.02) * env(int(RATE * 0.02), release=0.9) * 0.05
                out[i:i + len(s)] += s[:total - i]
    tmp = os.path.join(MUSIC, "theme.wav")
    save(tmp, out, 0.8)
    mp3 = os.path.join(MUSIC, "theme.mp3")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", tmp, "-b:a", "64k", mp3], check=True)
    os.remove(tmp)
    print("musique : %.1f s" % (total / RATE))


if __name__ == "__main__":
    gen_sfx()
    gen_music()
