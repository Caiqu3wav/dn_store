import { useEffect, useState } from 'react';

/** Devolve `value` só depois de ficar `delayMs` sem mudar; evita uma requisição por tecla digitada. */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
    const [debounced, setDebounced] = useState(value);

    useEffect(() => {
        const timer = setTimeout(() => setDebounced(value), delayMs);
        return () => clearTimeout(timer);
    }, [value, delayMs]);

    return debounced;
}
