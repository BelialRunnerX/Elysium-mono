"""
Synthesised sound: every clip the pack plays is generated here from a few
numbers, the way every sprite and every creature sheet is generated from rows.

No samples are recorded or downloaded. A voice is a description (a pitch, a
timbre, how much growl, how long it holds) and three clips come out of it:
what the thing says when idle, when hurt, and when it dies. A hum is a loop
for a machine that is running. A click is what a station says when opened.

Everything is deterministic: the same description and seed give the same
samples on every machine, which the drift check depends on (tools/samefile.py
compares a .ogg by its decoded samples, because the Ogg container carries a
stream serial the encoder picks fresh each time).

The pieces are plain numpy: oscillators, noise, envelopes, one-pole filters,
a comb for the metallic voices, and a tiny reverb. Mixing is in float32 at
SAMPLE_RATE; clips are normalised to a peak and written as OGG Vorbis through
soundfile (libsndfile), 22 050 Hz mono, which is what the game's own sounds
are.
"""
import math

import numpy as np

SAMPLE_RATE = 22050
PEAK = 0.82


# ---------------------------------------------------------------------------
# Primitives
# ---------------------------------------------------------------------------

def seconds(length):
    """The sample times for a clip of this many seconds."""
    return np.arange(int(round(length * SAMPLE_RATE)), dtype=np.float64) / SAMPLE_RATE


def envelope(n, attack, decay, sustain, release, hold=None):
    """
    An ADSR over n samples: attack and release in seconds, decay in seconds
    to the sustain level, held until the release begins.
    """
    out = np.zeros(n, dtype=np.float64)
    a = max(1, int(attack * SAMPLE_RATE))
    d = max(1, int(decay * SAMPLE_RATE))
    r = max(1, int(release * SAMPLE_RATE))
    if hold is None:
        h = max(0, n - a - d - r)
    else:
        h = max(0, min(int(hold * SAMPLE_RATE), n - a - d - r))
    at = 0
    out[at:at + a] = np.linspace(0.0, 1.0, a, endpoint=False)
    at += a
    out[at:at + d] = np.linspace(1.0, sustain, d, endpoint=False)
    at += d
    out[at:at + h] = sustain
    at += h
    rest = n - at
    if rest > 0:
        out[at:] = np.linspace(sustain, 0.0, rest)
    return out


def glide(n, start, end, curve=1.0):
    """A pitch that slides from start to end Hz over the clip, curved."""
    x = np.linspace(0.0, 1.0, n) ** curve
    return start + (end - start) * x


def oscillator(freq, shape="sine", phase=0.0):
    """
    A waveform at a (possibly moving) frequency. freq is an array of Hz per
    sample; the phase is integrated so a slide has no clicks.
    """
    freq = np.asarray(freq, dtype=np.float64)
    ph = phase + 2.0 * np.pi * np.cumsum(freq) / SAMPLE_RATE
    if shape == "sine":
        return np.sin(ph)
    if shape == "saw":
        return 2.0 * ((ph / (2.0 * np.pi)) % 1.0) - 1.0
    if shape == "square":
        return np.where(np.sin(ph) >= 0.0, 1.0, -1.0)
    if shape == "triangle":
        return 2.0 * np.abs(2.0 * ((ph / (2.0 * np.pi)) % 1.0) - 1.0) - 1.0
    raise ValueError("no such waveform: " + shape)


def noise(n, rng, colour="white"):
    """Noise, white or pink (pink through a cheap three-pole approximation)."""
    white = rng.standard_normal(n)
    if colour == "white":
        return white
    if colour == "pink":
        b0 = b1 = b2 = 0.0
        out = np.empty(n)
        for i in range(n):
            w = white[i]
            b0 = 0.99765 * b0 + w * 0.0990460
            b1 = 0.96300 * b1 + w * 0.2965164
            b2 = 0.57000 * b2 + w * 1.0526913
            out[i] = (b0 + b1 + b2 + w * 0.1848) * 0.2
        return out
    raise ValueError("no such noise: " + colour)


def lowpass(x, cutoff):
    """A one-pole low-pass; cutoff in Hz, a scalar or an array per sample."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=np.float64), x.shape)
    alpha = 1.0 - np.exp(-2.0 * np.pi * cutoff / SAMPLE_RATE)
    out = np.empty_like(x)
    y = 0.0
    for i in range(x.size):
        y += alpha[i] * (x[i] - y)
        out[i] = y
    return out


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def comb(x, delay_seconds, feedback):
    """A feedback comb: the ring of a metal body or a tube."""
    d = max(1, int(delay_seconds * SAMPLE_RATE))
    out = np.copy(x)
    for i in range(d, x.size):
        out[i] += feedback * out[i - d]
    return out


def reverb(x, size=0.05, mix=0.18, rng=None):
    """A small room: four combs and a touch of diffusion, mixed in lightly."""
    wet = np.zeros_like(x)
    for k, (d, fb) in enumerate(((size, 0.62), (size * 1.31, 0.58), (size * 1.67, 0.55), (size * 2.11, 0.5))):
        wet += comb(x, d, fb) / 4.0
    wet = lowpass(wet, 3200.0)
    return (1.0 - mix) * x + mix * wet


def distort(x, drive):
    """Soft clipping: growl."""
    return np.tanh(x * (1.0 + drive)) / math.tanh(1.0 + drive)


def normalise(x, peak=PEAK):
    top = float(np.max(np.abs(x))) if x.size else 0.0
    if top <= 1e-9:
        return x.astype(np.float32)
    return (x * (peak / top)).astype(np.float32)


def fade(x, seconds_in=0.004, seconds_out=0.02):
    """Short fades so no clip starts or ends on a step."""
    n = x.size
    a = min(n, max(1, int(seconds_in * SAMPLE_RATE)))
    r = min(n, max(1, int(seconds_out * SAMPLE_RATE)))
    x = np.copy(x)
    x[:a] *= np.linspace(0.0, 1.0, a)
    x[n - r:] *= np.linspace(1.0, 0.0, r)
    return x


# ---------------------------------------------------------------------------
# Voices: a creature's three clips from one description
# ---------------------------------------------------------------------------

class Voice:
    """
    What a creature sounds like.

    pitch    the fundamental in Hz (a brute low, a bee high)
    timbre   "throat" (formant-ish saw through a moving low-pass), "hiss"
             (filtered noise), "chirp" (pure tones that slide), "drone"
             (a detuned pair, for things that hum), "metal" (comb-rung)
    growl    0..1 soft clipping on the throat
    breath   0..1 noise mixed under the tone
    length   seconds for the ambient call; hurt is shorter, death longer
    vibrato  Hz of wobble on the pitch, and its depth as a share of pitch
    """

    def __init__(self, pitch, timbre="throat", growl=0.2, breath=0.15, length=0.9,
                 vibrato=(5.0, 0.02), body=0.0):
        self.pitch = float(pitch)
        self.timbre = timbre
        self.growl = float(growl)
        self.breath = float(breath)
        self.length = float(length)
        self.vibrato = vibrato
        self.body = float(body)

    def _tone(self, freq, rng, n):
        t = np.arange(n) / SAMPLE_RATE
        rate, depth = self.vibrato
        wobble = 1.0 + depth * np.sin(2.0 * np.pi * rate * t)
        f = freq * wobble
        if self.timbre == "throat":
            raw = 0.6 * oscillator(f, "saw") + 0.4 * oscillator(f * 0.5, "square")
            sweep = glide(n, f.mean() * 6.0, f.mean() * 2.5, 0.7)
            tone = lowpass(raw, sweep)
            tone = distort(tone, 6.0 * self.growl) if self.growl > 0 else tone
        elif self.timbre == "hiss":
            band = lowpass(noise(n, rng), f * 8.0) - lowpass(noise(n, rng), f * 2.0)
            tone = band * 1.4
        elif self.timbre == "chirp":
            tone = oscillator(f, "sine") + 0.35 * oscillator(f * 2.0, "sine")
        elif self.timbre == "drone":
            tone = 0.5 * oscillator(f, "triangle") + 0.5 * oscillator(f * 1.007, "triangle")
            tone = lowpass(tone, f * 5.0)
        elif self.timbre == "metal":
            tone = comb(0.3 * oscillator(f, "square") + 0.4 * noise(n, rng), 1.0 / max(60.0, float(f.mean()) * 0.5), 0.86)
            tone = highpass(tone, 120.0)
        else:
            raise ValueError("no such timbre: " + self.timbre)
        if self.breath > 0:
            tone = tone + self.breath * lowpass(noise(n, rng), max(400.0, f.mean() * 4.0))
        if self.body > 0:
            tone = reverb(tone, size=0.02 + 0.06 * self.body, mix=0.12 + 0.2 * self.body)
        return tone

    def ambient(self, rng):
        n = seconds(self.length).size
        f = glide(n, self.pitch * 0.9, self.pitch * 1.05, 0.5)
        env = envelope(n, 0.05, 0.15, 0.7, 0.35)
        return finish(self._tone(f, rng, n) * env)

    def hurt(self, rng):
        n = seconds(max(0.25, self.length * 0.45)).size
        f = glide(n, self.pitch * 1.6, self.pitch * 1.1, 0.6)
        env = envelope(n, 0.005, 0.08, 0.5, 0.12)
        return finish(self._tone(f, rng, n) * env)

    def death(self, rng):
        n = seconds(self.length * 1.7).size
        f = glide(n, self.pitch * 1.2, self.pitch * 0.35, 1.4)
        env = envelope(n, 0.01, 0.4, 0.6, 0.7)
        return finish(self._tone(f, rng, n) * env)

    def clips(self, seed):
        """The three, each from its own stream of the one seed."""
        return {
            "ambient": self.ambient(np.random.default_rng(seed * 3 + 0)),
            "hurt": self.hurt(np.random.default_rng(seed * 3 + 1)),
            "death": self.death(np.random.default_rng(seed * 3 + 2)),
        }


# ---------------------------------------------------------------------------
# Hums: a running machine, looped
# ---------------------------------------------------------------------------

class Hum:
    """
    What a machine sounds like while it runs, as a loop of `length` seconds
    that joins onto itself: the tail is crossfaded into the head.

    kind     "shaft" (wooden rattle), "steam" (hiss and thump), "diesel"
             (a knocking pulse), "electric" (a mains hum with harmonics),
             "reactor" (a deep pulse with a whine), "roar" (a furnace),
             "whine" (a centrifuge), "pump" (a slow stroke)
    pitch    Hz of the fundamental where there is one
    """

    def __init__(self, kind, pitch=110.0, length=2.0, level=0.6):
        self.kind = kind
        self.pitch = float(pitch)
        self.length = float(length)
        self.level = float(level)

    def render(self, seed):
        rng = np.random.default_rng(seed)
        n = seconds(self.length).size
        t = np.arange(n) / SAMPLE_RATE
        f = np.full(n, self.pitch)
        k = self.kind
        if k == "shaft":
            pulse = (np.sin(2 * np.pi * self.pitch / 20.0 * t) > 0.92).astype(float)
            x = lowpass(noise(n, rng), 900.0) * (0.3 + pulse) + 0.15 * oscillator(f * 0.25, "triangle")
        elif k == "steam":
            x = lowpass(noise(n, rng), 2400.0) * 0.8 + 0.3 * (np.sin(2 * np.pi * 2.0 * t) > 0.95) * lowpass(noise(n, rng), 300.0)
        elif k == "diesel":
            knock = (np.sin(2 * np.pi * 9.0 * t) > 0.85).astype(float)
            x = lowpass(noise(n, rng), 500.0) * (0.2 + knock) + 0.3 * oscillator(f * 0.5, "saw")
            x = lowpass(x, 700.0)
        elif k == "electric":
            x = oscillator(f, "sine") + 0.4 * oscillator(f * 2.0, "sine") + 0.2 * oscillator(f * 3.0, "sine") + 0.05 * lowpass(noise(n, rng), 1500.0)
        elif k == "reactor":
            pulse = 0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t)
            x = (oscillator(f * 0.5, "sine") + 0.3 * oscillator(f * 0.5 * 1.01, "sine")) * pulse + 0.12 * oscillator(f * 8.0, "sine")
        elif k == "roar":
            x = lowpass(noise(n, rng), 600.0) * 1.2 + 0.2 * oscillator(f * 0.5, "triangle") * (0.7 + 0.3 * np.sin(2 * np.pi * 3.0 * t))
        elif k == "whine":
            x = oscillator(f * 4.0, "sine") * 0.6 + 0.3 * oscillator(f * 4.0 * 1.003, "sine") + 0.15 * highpass(noise(n, rng), 3000.0)
        elif k == "pump":
            stroke = 0.5 + 0.5 * np.sin(2 * np.pi * 0.75 * t)
            x = lowpass(noise(n, rng), 400.0) * stroke + 0.2 * oscillator(f * 0.5, "sine") * stroke
        else:
            raise ValueError("no such hum: " + k)
        x = x * self.level
        return finish(loop(x), fade_out=0.0, fade_in=0.0)


def loop(x, cross=0.15):
    """Crossfade the tail into the head so the clip repeats without a seam."""
    c = min(x.size // 3, max(1, int(cross * SAMPLE_RATE)))
    head = x[:c]
    tail = x[-c:]
    ramp = np.linspace(0.0, 1.0, c)
    body = np.copy(x[:-c])
    body[:c] = tail * (1.0 - ramp) + head * ramp
    return body


# ---------------------------------------------------------------------------
# Clicks and one-offs
# ---------------------------------------------------------------------------

def click(kind, seed, pitch=1800.0):
    """A station opening, a plate snapping on, a thing switching."""
    rng = np.random.default_rng(seed)
    if kind == "station":
        n = seconds(0.16).size
        x = oscillator(glide(n, pitch, pitch * 0.6, 0.4), "sine") * envelope(n, 0.001, 0.04, 0.2, 0.08) \
            + 0.5 * highpass(noise(n, rng), 2500.0) * envelope(n, 0.001, 0.02, 0.0, 0.02)
    elif kind == "snap":
        n = seconds(0.12).size
        x = comb(highpass(noise(n, rng), 1200.0), 1.0 / pitch, 0.7) * envelope(n, 0.001, 0.03, 0.1, 0.06)
    elif kind == "switch":
        n = seconds(0.1).size
        x = oscillator(np.full(n, pitch * 0.5), "square") * envelope(n, 0.001, 0.015, 0.0, 0.02) * 0.4 \
            + 0.6 * lowpass(noise(n, rng), 3000.0) * envelope(n, 0.001, 0.01, 0.0, 0.03)
    else:
        raise ValueError("no such click: " + kind)
    return finish(x)


def finish(x, fade_in=0.004, fade_out=0.02):
    """Normalise, fade, float32."""
    x = np.asarray(x, dtype=np.float64)
    if fade_in or fade_out:
        x = fade(x, fade_in or 0.0, fade_out or 0.0)
    return normalise(x)


def write_ogg(path, samples):
    """One mono OGG Vorbis clip at SAMPLE_RATE."""
    import soundfile
    path.parent.mkdir(parents=True, exist_ok=True)
    soundfile.write(str(path), np.asarray(samples, dtype=np.float32), SAMPLE_RATE, format="OGG", subtype="VORBIS")
