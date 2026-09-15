import { useState, useCallback } from 'react';
import api from '../lib/axios';

interface UseApiState<T> {
  data: T | null;
  error: string | null;
  loading: boolean;
}

export function useApi<T = any>() {
  const [state, setState] = useState<UseApiState<T>>({
    data: null,
    error: null,
    loading: false,
  });

  const request = useCallback(async (
    method: 'get' | 'post' | 'put' | 'delete',
    url: string,
    body?: any,
    config?: any
  ): Promise<T> => {
    setState(prev => ({ ...prev, loading: true, error: null }));
    try {
      const response = await api({
        method,
        url,
        data: body,
        ...config
      });
      setState({ data: response.data, error: null, loading: false });
      return response.data;
    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || 'Erro ao processar requisição.';
      setState({ data: null, error: errorMsg, loading: false });
      throw err;
    }
  }, []);

  return { ...state, request };
}
