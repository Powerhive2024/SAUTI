"""Python copy of the app's LoopGuard (android/.../edge/LoopGuard.kt): cut a trailing repetition loop."""
MAX_PERIOD = 150


def _window(p):
    return max(24, p * (6 if p < 8 else 3))


def cut(text):
    n = len(text)
    for p in range(1, MAX_PERIOD + 1):
        w = _window(p)
        if w > n:
            break
        if any(text[i] != text[i - p] for i in range(n - w + p, n)):
            continue
        start = n - w
        while start > 0 and text[start - 1] == text[start - 1 + p]:
            start -= 1
        keep = start + p
        while keep < n and not text[keep].isspace():
            keep += 1
        return keep
    return None


def clean(text):
    k = cut(text)
    return text if k is None else text[:k].rstrip(" ,-")
