import { httpClient } from '../lib/api/httpClient';

export type Cctv = {
  cctvId: number;
  cctvName: string;
  cctvNum: string;
  location: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CctvPayload = {
  cctvName: string;
  cctvNum: string;
  location?: string;
  isActive?: boolean;
};

export async function getCctvs() {
  const response = await httpClient.get<Cctv[]>('/api/v1/cctvs');
  return response.data;
}

export async function createCctv(payload: CctvPayload) {
  const response = await httpClient.post<Cctv>('/api/v1/cctvs', payload);
  return response.data;
}

export async function updateCctv(cctvId: number, payload: CctvPayload) {
  const response = await httpClient.patch<Cctv>(`/api/v1/cctvs/${cctvId}`, payload);
  return response.data;
}

export async function deactivateCctv(cctvId: number) {
  const response = await httpClient.delete<Cctv>(`/api/v1/cctvs/${cctvId}`);
  return response.data;
}
