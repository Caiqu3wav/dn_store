import { useSyncExternalStore } from 'react';

const mediaQuery = '(prefers-reduced-motion: reduce)';

function subscribe(onChange: () => void) {
    const mediaQueryList = window.matchMedia(mediaQuery);
    mediaQueryList.addEventListener('change', onChange);
    return () => mediaQueryList.removeEventListener('change', onChange);
}

function getSnapshot() {
    return window.matchMedia(mediaQuery).matches;
}

function getServerSnapshot() {
    return false;
}

export function usePrefersReducedMotion() {
    return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}
