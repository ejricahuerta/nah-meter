"""
The showcase's music: a dark Toronto trap beat, composed and synthesised here from oscillators and
noise (no samples, no soundfont, no General MIDI). Writes audio/build/music-trap.wav.

140 bpm in half time, F minor (Fm - Db - Ab - Ebm). The grid starts at timeline.musicInSec, when the
first verdict lands, so the beat drops on it; the arrangement follows src/timeline.json:

  hook     only a low pad under the timer ticks (make-audio.mjs adds those)
  drop     808, kick, clap, hats, bell, from the first verdict
  scenes   a hat roll and a reversed bell swell into each scene change
  road     hats and bell filtered down, so "says it out loud" stands out
  trust    drums out: pad, bell and long 808 notes ("Never taps for you")
  end      one last 808 and kick as the red square lands, then the bell's tail

Seeded, so every build sounds the same. Needs numpy.
Run: python3 scripts/make-trap.py   (then make-audio.mjs with MUSIC=audio/build/music-trap.wav)
"""

import json
import os
import wave

import numpy as np

ROOT = os.path.join(os.path.dirname(__file__), "..")
T = json.load(open(os.path.join(ROOT, "src", "timeline.json")))
SR = 48000
N = int(round(T["durationSec"] * SR))
S = T["scenes"]
rng = np.random.default_rng(140)

BPM = 140
BEAT = 60 / BPM
STEP = BEAT / 4  # a sixteenth
BAR = BEAT * 4
T0 = T["musicInSec"]
STOP = T["endCue"]["stopSec"]

# Fm - Db - Ab - Ebm: the roots (MIDI) and the chord tones for the bell and the pad.
CHORDS = [
    (41, [65, 68, 72]),  # F:  F4 Ab4 C5
    (37, [61, 65, 68]),  # Db: Db4 F4 Ab4
    (44, [68, 72, 75]),  # Ab: Ab4 C5 Eb5
    (39, [63, 66, 70]),  # Ebm: Eb4 Gb4 Bb4
]


def hz(m):
    return 440.0 * 2 ** ((m - 69) / 12)


def t_of(n):
    return np.arange(n) / SR


# ---------------------------------------------------------------- helpers

def fft_filter(x, lo=None, hi=None, order=2):
    """Butterworth-shaped band filter in the frequency domain (whole signal at once)."""
    n = len(x)
    size = 1 << int(np.ceil(np.log2(n + 1)))
    X = np.fft.rfft(x, size)
    f = np.fft.rfftfreq(size, 1 / SR)
    g = np.ones_like(f)
    if hi:
        g /= np.sqrt(1 + (f / hi) ** (2 * order))
    if lo:
        g /= np.sqrt(1 + (lo / np.maximum(f, 1e-6)) ** (2 * order))
    return np.fft.irfft(X * g, size)[:n]


def reverb(x, seconds=2.2, decay=0.45, tone=6000):
    """Convolution with a decaying stereo-ish noise tail (one channel)."""
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-t_of(n) / decay)
    ir = fft_filter(ir, lo=180, hi=tone)
    ir[: int(0.012 * SR)] = 0  # a short pre-delay
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    y = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]
    return y


def place(bus, at, sig, gain=1.0):
    i = int(round(at * SR))
    if i >= len(bus) or i + len(sig) <= 0:
        return
    a, b = max(0, i), min(len(bus), i + len(sig))
    bus[a:b] += sig[a - i : b - i] * gain


def fades(sig, a=0.002, r=0.01):
    na, nr = int(a * SR), int(r * SR)
    env = np.ones(len(sig))
    if na:
        env[:na] = np.linspace(0, 1, na)
    if nr:
        env[-nr:] *= np.linspace(1, 0, nr)
    return sig * env


# ---------------------------------------------------------------- instruments

def eight08(freq, length, from_freq=None, drive=2.2):
    """The 808: a sine that drops in from an octave up (or glides from the last note), long decay,
    saturated so its harmonics carry on phone speakers."""
    t = t_of(int(length * SR))
    if from_freq:
        f = freq + (from_freq - freq) * np.exp(-t / 0.045)
    else:
        f = freq * (1 + 1.0 * np.exp(-t / 0.012))
    phase = 2 * np.pi * np.cumsum(f) / SR
    amp = np.exp(-t / 0.75)
    clean = np.sin(phase) * amp
    # Part clean, part driven: enough harmonics to be heard on a phone, without a square-wave buzz.
    y = 0.55 * clean + 0.45 * np.tanh(drive * clean) / np.tanh(drive)
    return fades(y, 0.001, 0.02)


def kick():
    t = t_of(int(0.32 * SR))
    f = 48 + 120 * np.exp(-t / 0.03)
    body = np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-t / 0.11)
    click = fft_filter(rng.standard_normal(len(t)), lo=2500) * np.exp(-t / 0.002) * 0.5
    return fades(np.tanh(1.8 * (body + click)), 0.0005, 0.01)


def clap():
    t = t_of(int(0.45 * SR))
    noise = fft_filter(rng.standard_normal(len(t)), lo=900, hi=4200)
    env = np.zeros(len(t))
    for k, off in enumerate((0.0, 0.011, 0.023)):
        tt = np.clip(t - off, 0, None)
        env += (t >= off) * np.exp(-tt / 0.006) * (0.8 if k < 2 else 1.0)
    env += (t >= 0.023) * np.exp(-np.clip(t - 0.023, 0, None) / 0.11) * 0.55
    return fades(noise * env / np.max(np.abs(noise * env)), 0.0005, 0.02)


def hat(open_=False):
    n = int((0.32 if open_ else 0.07) * SR)
    t = t_of(n)
    noise = fft_filter(rng.standard_normal(n), lo=7200, hi=15000)
    y = noise * np.exp(-t / (0.11 if open_ else 0.018))
    return fades(y / np.max(np.abs(y)), 0.0003, 0.01)


def bell(freq, length=1.6):
    """FM bell: a bright strike that mellows (modulator at 3.5×)."""
    t = t_of(int(length * SR))
    index = 4.0 * np.exp(-t / 0.18) + 0.4
    y = np.sin(2 * np.pi * freq * t + index * np.sin(2 * np.pi * 3.5 * freq * t))
    y += 0.25 * np.sin(2 * np.pi * 2 * freq * t) * np.exp(-t / 0.3)
    return fades(y * np.exp(-t / 0.55), 0.002, 0.05)


def pad(midis, length, detune_seed):
    """Detuned saws, softened later by a low-pass on the whole pad bus."""
    t = t_of(int(length * SR))
    y = np.zeros(len(t))
    r = np.random.default_rng(detune_seed)
    for m in midis:
        for cents in (-9, 0, 8):
            f = hz(m) * 2 ** ((cents + r.uniform(-2, 2)) / 1200)
            ph = r.uniform(0, 1)
            y += 2 * ((f * t + ph) % 1.0) - 1
    att, rel = min(0.5, length / 3), min(0.7, length / 3)
    env = np.minimum(1, np.minimum(t / att, (length - t) / rel))
    return y * np.clip(env, 0, 1) / (3 * len(midis))


# ---------------------------------------------------------------- arrangement

low = np.zeros(N)       # 808 + kick, centre
clp = np.zeros(N)       # clap, centre
hats = np.zeros((2, N))  # hats, panned
bells = np.zeros((2, N))
pads = np.zeros((2, N))

KICK, CLAP, HAT, OHAT = kick(), clap(), hat(), hat(True)

def in_range(t, key):
    a, b = S[key]
    return a <= t < b

# Scene changes after the drop, for the hat rolls and swells (and the end's last hit).
changes = sorted(v[0] for k, v in S.items() if v[0] > T0 + 0.5)

# 808 rhythms (sixteenth steps in a bar); the last one slides into the next bar's root.
PATTERNS = [
    [(0, 0), (6, 0), (10, 0), (14, 12)],
    [(0, 0), (3, 0), (8, 0), (11, 0), (14, -2)],
]
HAT_LEVEL = [1.0, 0.55, 0.8, 0.55]  # accents within each beat

first_bar = int(np.floor((0 - T0) / BAR)) - 1
last_bar = int(np.ceil((T["durationSec"] - T0) / BAR)) + 1
prev_808 = None
for b in range(first_bar, last_bar):
    bar_t = T0 + b * BAR
    root, tones = CHORDS[b % 4]
    drums = bar_t >= T0 - 1e-6 and bar_t < STOP

    # Pad: always (the only thing before the drop).
    if bar_t + BAR > 0 and bar_t < T["durationSec"]:
        p = pad([tones[0] - 12, tones[1] - 12, tones[2] - 12], BAR + 0.6, 1000 + b)
        place(pads[0], bar_t, p, 1.0)
        place(pads[1], bar_t + 0.011, pad([tones[0] - 12, tones[1] - 12, tones[2] - 12], BAR + 0.6, 2000 + b), 1.0)

    if not drums:
        continue  # before the drop: the pad alone

    trust = in_range(bar_t + BAR / 2, "trust") if "trust" in S else False
    road = in_range(bar_t + BAR / 2, "road") if "road" in S else False

    # 808 (and the kick on its hits, except in trust).
    pat = PATTERNS[1 if b % 2 else 0]
    if trust:
        pat = [(0, 0)]
    for i, (step, shift) in enumerate(pat):
        at = bar_t + step * STEP
        if at >= STOP:
            break
        nxt = pat[i + 1][0] * STEP + bar_t if i + 1 < len(pat) else bar_t + BAR
        length = min(nxt - at + 0.02, 1.6) if not trust else BAR
        m = root + 12 + shift  # octave 2 so phone speakers carry it
        f = hz(m)
        glide = prev_808 if shift and prev_808 else None
        place(low, at, eight08(f, length, from_freq=glide), 0.95)
        if not trust and step in (0, 10) or (not trust and step == 8):
            place(low, at, KICK, 0.65)
        prev_808 = f

    # Clap on beat 3 (half time), with an extra ghost now and then.
    if not trust:
        place(clp, bar_t + 8 * STEP, CLAP, 1.0)
        if b % 4 == 3:
            place(clp, bar_t + 15 * STEP, CLAP, 0.35)

    # Hats: eighths, sixteenths in the second half of odd bars; a roll into each scene change.
    if not trust:
        change_in_bar = [c for c in changes if bar_t < c <= bar_t + BAR + 1e-6]
        steps = []
        for s in range(16):
            if s % 2 == 0 or (b % 2 and s >= 8):
                steps.append((s * STEP, HAT_LEVEL[s % 4]))
        if change_in_bar:
            # Replace the last half-beat or more before the change with a 32nd-triplet roll.
            c = change_in_bar[0]
            roll_from = c - BEAT
            steps = [(o, v) for (o, v) in steps if bar_t + o < roll_from]
            k = 0
            t = roll_from
            while t < c - 0.01:
                steps.append((t - bar_t, 0.45 + 0.5 * (t - roll_from) / BEAT))
                t += STEP / 3 * 2 if k < 3 else STEP / 3
                k += 1
        for k, (o, v) in enumerate(steps):
            at = bar_t + o
            if at >= STOP:
                continue
            pan = 0.25 if k % 2 else -0.25
            g = v * (0.9 + 0.2 * rng.random())
            place(hats[0], at, HAT, g * (1 - pan))
            place(hats[1], at, HAT, g * (1 + pan))
        if b % 2 == 1 and bar_t + 14 * STEP < STOP:
            place(hats[0], bar_t + 14 * STEP, OHAT, 0.35)
            place(hats[1], bar_t + 14 * STEP, OHAT, 0.45)

    # Bell: a dark arpeggio on the chord, high, with a ping-pong delay (added below).
    if bar_t < STOP:
        line = [(0, tones[2]), (3, tones[1]), (6, tones[0]), (10, tones[1]), (12, tones[2] + (2 if b % 4 == 1 else 0))]
        if b % 8 >= 4:
            line = [(0, tones[2] + 12), (4, tones[2]), (6, tones[1]), (10, tones[0]), (14, tones[1])]
        for step, m in line:
            at = bar_t + step * STEP
            if at < STOP:
                place(bells[0], at, bell(hz(m)), 0.8)
                place(bells[1], at, bell(hz(m)), 0.8)

# Reversed bell swells into the drop and each scene change.
for c in [T0] + changes:
    sw = bell(hz(CHORDS[0][1][2]), 1.2)[::-1] * np.linspace(0, 1, int(1.2 * SR)) ** 2
    place(bells[0], c - 1.2, sw, 0.55)
    place(bells[1], c - 1.2, sw, 0.55)

# The end: one last 808 and kick as the red square lands, a bell chord ringing out.
place(low, STOP, eight08(hz(CHORDS[0][0] + 12), 2.4), 1.0)
place(low, STOP, KICK, 0.8)
for m in CHORDS[0][1]:
    place(bells[0], STOP, bell(hz(m), 3.0), 0.55)
    place(bells[1], STOP + 0.013, bell(hz(m), 3.0), 0.55)

# ---------------------------------------------------------------- mix

# Road: hats and bell filtered down (a smooth crossfade in and out).
def window(a, b, ramp=0.25):
    t = t_of(N)
    return np.clip(np.minimum((t - a) / ramp, (b - t) / ramp), 0, 1)

if "road" in S:
    w = window(*S["road"])
    for bus, cut in ((hats, 1800), (bells, 1400)):
        for c in range(2):
            bus[c] = bus[c] * (1 - w) + fft_filter(bus[c], hi=cut, order=3) * w
    clp *= 1 - 0.5 * window(*S["road"])

# The pad: dark and soft, opening up a little after the drop.
for c in range(2):
    pads[c] = fft_filter(pads[c], hi=900, order=2)
pads *= np.where(t_of(N) < T0, 0.7, 1.0)

# Ping-pong delay on the bell (dotted eighth), then a room.
delay = int(BEAT * 0.75 * SR)
pp = np.zeros((2, N))
pp[0] += bells[0]
pp[1] += bells[1]
for k in range(1, 6):
    src = bells[(k + 1) % 2]
    ch = k % 2
    d = delay * k
    pp[ch][d:] += src[:-d] * (0.45 ** k)
bells = pp

def norm(x):
    p = np.max(np.abs(x))
    return x / p if p else x

low = fft_filter(low, hi=1600, order=2)  # keep the 808's grit below the voice's range
low, clp = norm(low), norm(clp)
hats = norm(hats)
bells = norm(bells)
pads = norm(pads)

verb_send = clp * 0.5 + (bells[0] + bells[1]) * 0.25
verb = norm(reverb(verb_send))
verb_r = np.roll(verb, int(0.017 * SR))

mix = np.zeros((2, N))
for c in range(2):
    mix[c] += low * 0.95
    mix[c] += clp * 0.42
    mix[c] += hats[c] * 0.22
    mix[c] += bells[c] * 0.26
    mix[c] += pads[c] * 0.2
    mix[c] += (verb if c == 0 else verb_r) * 0.14

# Glue: gentle saturation, then normalise.
mix = np.tanh(mix * 1.2)
mix /= np.max(np.abs(mix)) / 0.9

out = os.path.join(ROOT, "audio", "build", "music-trap.wav")
os.makedirs(os.path.dirname(out), exist_ok=True)
pcm = (mix.T * 32767).astype("<i2")
with wave.open(out, "wb") as w:
    w.setnchannels(2)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(pcm.tobytes())
print(f"wrote {out} ({T['durationSec']} s, {BPM} bpm, drop at {T0:.2f} s, stop at {STOP:.2f} s)")
