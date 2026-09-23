'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { useAuth } from '../context/AuthContext';
import { accountService, Address } from '@/services/accountService';

type Order = { id: string; status: string; total: number; createdAt: string };

const emptyAddress: Address = { street: '', number: '', complement: '', neighborhood: '', city: '', state: '', zipCode: '' };

export default function AccountPage() {
  const { user, loading: authLoading } = useAuth();
  const [profile, setProfile] = useState({ name: '', phone: '' });
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [address, setAddress] = useState(emptyAddress);
  const [showAddress, setShowAddress] = useState(false);
  const [message, setMessage] = useState('');

  useEffect(() => {
    if (!user) return;
    Promise.all([accountService.getProfile(), accountService.getAddresses(), accountService.getOrders()])
      .then(([data, savedAddresses, savedOrders]) => {
        setProfile({ name: data.name || '', phone: data.phone || '' });
        setAddresses(savedAddresses);
        setOrders(savedOrders);
      })
      .catch(() => setMessage('Não foi possível carregar os dados da conta.'));
  }, [user]);

  if (authLoading) return <main className="min-h-screen pt-32 text-center">Carregando...</main>;
  if (!user) return <main className="min-h-screen pt-32 text-center"><Link className="text-brand-secondary font-bold" href="/auth?next=/conta">Entrar para acessar sua conta</Link></main>;

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault();
    await accountService.updateProfile(profile);
    setMessage('Dados atualizados.');
  };

  const saveAddress = async (event: FormEvent) => {
    event.preventDefault();
    const saved = await accountService.addAddress(address);
    setAddresses(current => [...current, saved]);
    setAddress(emptyAddress);
    setShowAddress(false);
  };

  return (
    <main className="min-h-screen bg-gray-50 pt-28 pb-20 text-gray-900">
      <div className="mx-auto max-w-6xl px-4 space-y-6">
        <header className="flex flex-wrap items-end justify-between gap-4">
          <div><p className="text-sm text-brand-secondary font-bold uppercase">Minha conta</p><h1 className="text-3xl font-black">Olá, {user.name}</h1></div>
          {user.role === 'ADMIN' && <Link href="/admin" className="rounded-lg bg-brand-secondary px-5 py-3 font-bold text-white shadow-sm transition hover:bg-red-700">Acessar painel admin</Link>}
        </header>
        {message && <p className="rounded-lg bg-green-50 p-3 text-sm text-green-700">{message}</p>}
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <h2 className="mb-4 text-xl font-bold">Meus dados</h2>
            <form onSubmit={saveProfile} className="space-y-4">
              <input value={profile.name} onChange={e => setProfile({ ...profile, name: e.target.value })} placeholder="Nome completo" className="w-full rounded-lg border p-3" required />
              <input value={user.email} readOnly className="w-full rounded-lg border bg-gray-50 p-3 text-gray-500" />
              <input value={profile.phone} onChange={e => setProfile({ ...profile, phone: e.target.value })} placeholder="Telefone" className="w-full rounded-lg border p-3" />
              <button className="rounded-lg bg-brand-primary px-5 py-3 font-bold text-white">Salvar dados</button>
            </form>
          </section>
          <section className="rounded-2xl bg-white p-6 shadow-sm">
            <div className="mb-4 flex items-center justify-between"><h2 className="text-xl font-bold">Endereços</h2><button onClick={() => setShowAddress(!showAddress)} className="text-sm font-bold text-brand-secondary">{showAddress ? 'Fechar' : 'Adicionar'}</button></div>
            {showAddress && <form onSubmit={saveAddress} className="mb-5 grid grid-cols-2 gap-3">
              {(['zipCode', 'street', 'number', 'complement', 'neighborhood', 'city', 'state'] as const).map(field => <input key={field} value={address[field] || ''} onChange={e => setAddress({ ...address, [field]: e.target.value })} placeholder={field} className="rounded-lg border p-3 text-sm first:col-span-2" required={['zipCode', 'street', 'number', 'city', 'state'].includes(field)} />)}
              <button className="col-span-2 rounded-lg bg-brand-primary px-4 py-3 font-bold text-white">Salvar endereço</button>
            </form>}
            {addresses.length === 0 ? <p className="text-sm text-gray-500">Nenhum endereço salvo.</p> : addresses.map(item => <div key={item.id} className="mb-3 flex justify-between rounded-lg border p-3 text-sm"><span>{item.street}, {item.number} - {item.city}/{item.state}<br />CEP {item.zipCode}</span><button onClick={async () => { await accountService.deleteAddress(item.id!); setAddresses(current => current.filter(addressItem => addressItem.id !== item.id)); }} className="text-red-600">Excluir</button></div>)}
          </section>
        </div>
        <section className="rounded-2xl bg-white p-6 shadow-sm"><h2 className="mb-4 text-xl font-bold">Pedidos e compras</h2>{orders.length === 0 ? <p className="text-sm text-gray-500">Você ainda não possui compras.</p> : <div className="space-y-3">{orders.map(order => <div key={order.id} className="flex flex-wrap justify-between gap-3 rounded-lg border p-4"><span>Pedido #{order.id.slice(0, 8)}<br /><small className="text-gray-500">{new Date(order.createdAt).toLocaleDateString('pt-BR')}</small></span><span className="font-bold">{order.status}</span><span>R$ {Number(order.total).toFixed(2).replace('.', ',')}</span></div>)}</div>}</section>
      </div>
    </main>
  );
}