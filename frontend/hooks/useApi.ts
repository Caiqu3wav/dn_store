import { useState, useCallback } from 'react';
import axios, { AxiosRequestConfig } from 'axios';
import api from '../lib/axios';

interface UseApiState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

export function useApi<T = unknown>() {
  const [state, setState] = useState<UseApiState<T>>({
    data: null,
    error: null,
    loading: false,
  });

  const request = useCallback(async (
    method: 'get' | 'post' | 'put' | 'delete',
    url: string,
    body?: unknown,
    config?: AxiosRequestConfig
  ): Promise<T> => {
    setState(prev => ({ ...prev, loading: true, error: null }));
    try {
      const response = await api<T>({
        method,
        url,
        data: body,
        ...config
      });
      setState({ data: response.data, error: null, loading: false });
      return response.data;
    } catch (err: unknown) {
      const errorMsg = axios.isAxiosError<{ message?: string }>(err)
        ? err.response?.data?.message || err.message || 'Erro ao processar requisição.'
        : err instanceof Error
          ? err.message
          : 'Erro ao processar requisição.';
      setState({ data: null, error: errorMsg, loading: false });
      throw err;
    }
  }, []);

  return { ...state, request };
}
