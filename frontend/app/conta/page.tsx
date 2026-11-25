'use client';

import { FormEvent, useEffect, useState } from 'react';
import Link from 'next/link';
import { MapPin } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { accountService, Address } from '@/services/accountService';
import { authService } from '@/services/authService';
import { getApiErrorMessage } from '@/utils/apiError';
import { formatCep, formatPhone } from '@/utils/authFormatters';
import { orderStatusLabel } from '@/utils/orderStatus';

type Order = { id: string; status: string; total: number; createdAt: string };

const cardClass = 'scroll-mt-28 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-200/70';
const cardTitleClass = 'mb-5 text-xl font-bold';
const inputClass = 'w-full rounded-lg border border-gray-300 bg-white p-3 outline-none transition focus:border-brand-red-primary focus:ring-2 focus:ring-brand-red-primary/20';
const primaryButtonClass = 'rounded-lg bg-brand-red-primary px-5 py-3 font-bold text-white transition hover:bg-red-900 disabled:opacity-60';

const emptyAddress: Address = { street: '', number: '', complement: '', neighborhood: '', city: '', state: '', zipCode: '' };

const ADDRESS_FIELDS = [
  { field: 'zipCode', label: 'CEP', placeholder: '00000-000', required: true },
  { field: 'street', label: 'Rua', placeholder: 'Nome da rua', required: true },
  { field: 'number', label: 'Número', placeholder: '123', required: true },
  { field: 'complement', label: 'Complemento', placeholder: 'Apto, bloco... (opcional)', required: false },
  { field: 'neighborhood', label: 'Bairro', placeholder: 'Bairro', required: false },
  { field: 'city', label: 'Cidade', placeholder: 'Cidade', required: true },
  { field: 'state', label: 'UF', placeholder: 'SP', required: true },
] as const;

export default function AccountPage() {
  const { user, loading: authLoading } = useAuth();
  const [profile, setProfile] = useState({ name: '', phone: '' });
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [address, setAddress] = useState(emptyAddress);
  const [showAddress, setShowAddress] = useState(false);
  const [message, setMessage] = useState('');
  const [messageIsError, setMessageIsError] = useState(false);
  const [isSavingProfile, setIsSavingProfile] = useState(false);
  const [isSavingAddress, setIsSavingAddress] = useState(false);

  const notify = (text: string, isError = false) => {
    setMessage(text);
    setMessageIsError(isError);
  };
  const [passwordForm, setPasswordForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' });
  const [passwordMessage, setPasswordMessage] = useState('');
  const [passwordError, setPasswordError] = useState('');
  const [isChangingPassword, setIsChangingPassword] = useState(false);
  const [emailMfaEnabled, setEmailMfaEnabled] = useState(false);
  const [isUpdatingMfa, setIsUpdatingMfa] = useState(false);
  const [mfaMessage, setMfaMessage] = useState('');
  const [mfaError, setMfaError] = useState('');

  useEffect(() => {
    if (!user) return;

    accountService.getProfile()
      .then(data => {
        setProfile({ name: data.name || '', phone: data.phone || '' });
        setEmailMfaEnabled(data.emailMfaEnabled);
      })
      .catch(error => notify(getApiErrorMessage(error, 'Não foi possível carregar os dados do perfil.'), true));

    accountService.getAddresses()
      .then(setAddresses)
      .catch(error => notify(getApiErrorMessage(error, 'Não foi possível carregar os endereços salvos.'), true));

    accountService.getOrders()
      .then(setOrders)
      .catch(error => notify(getApiErrorMessage(error, 'Não foi possível carregar os pedidos.'), true));
  }, [user]);

  if (authLoading) return <div className="min-h-screen pt-32 text-center">Carregando...</div>;
  if (!user) return <div className="min-h-screen pt-32 text-center"><Link className="text-brand-red-primary font-bold" href="/auth?next=/conta">Entrar para acessar sua conta</Link></div>;

  const saveProfile = async (event: FormEvent) => {
    event.preventDefault();
    if (isSavingProfile) return;
    const phoneDigits = profile.phone.replace(/\D/g, '');
    if (phoneDigits && (phoneDigits.length < 10 || phoneDigits.length > 11)) {
      notify('Informe um telefone válido com DDD.', true);
      return;
    }
    setIsSavingProfile(true);
    try {
      await accountService.updateProfile(profile);
      notify('Dados atualizados.');
    } catch (error: unknown) {
      notify(getApiErrorMessage(error, 'Não foi possível atualizar seus dados.'), true);
    } finally {
      setIsSavingProfile(false);
    }
  };

  const saveAddress = async (event: FormEvent) => {
    event.preventDefault();
    if (isSavingAddress) return;
    if (address.zipCode.replace(/\D/g, '').length !== 8) {
      notify('Informe um CEP válido com 8 dígitos.', true);
      return;
    }
    setIsSavingAddress(true);
    try {
      const saved = await accountService.addAddress({
        ...address,
        zipCode: address.zipCode.replace(/\D/g, ''),
        state: address.state.trim().toUpperCase(),
      });
      setAddresses(current => [...current, saved]);
      setAddress(emptyAddress);
      setShowAddress(false);
      notify('Endereço salvo.');
    } catch (error: unknown) {
      notify(getApiErrorMessage(error, 'Não foi possível salvar o endereço.'), true);
    } finally {
      setIsSavingAddress(false);
    }
  };

  const removeAddress = async (item: Address) => {
    if (!item.id || !confirm('Deseja excluir este endereço?')) return;
    try {
      await accountService.deleteAddress(item.id);
      setAddresses(current => current.filter(addressItem => addressItem.id !== item.id));
      notify('Endereço excluído.');
    } catch (error: unknown) {
      notify(getApiErrorMessage(error, 'Não foi possível excluir o endereço.', {
        409: 'Este endereço está vinculado a um pedido e não pode ser excluído.',
      }), true);
    }
  };

  const changePassword = async (event: FormEvent) => {
    event.preventDefault();
    setPasswordMessage('');
    setPasswordError('');
    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError('As novas senhas não coincidem.');
      return;
    }
    if (passwordForm.newPassword.length < 8 || !/[A-Za-z]/.test(passwordForm.newPassword) || !/\d/.test(passwordForm.newPassword)) {
      setPasswordError('Use pelo menos 8 caracteres, incluindo uma letra e um número.');
      return;
    }

    setIsChangingPassword(true);
    try {
      await authService.changePassword({ currentPassword: passwordForm.currentPassword, newPassword: passwordForm.newPassword });
      setPasswordForm({ currentPassword: '', newPassword: '', confirmPassword: '' });
      setPasswordMessage('Senha atualizada com sucesso.');
    } catch (error: unknown) {
      setPasswordError(getApiErrorMessage(error, 'Não foi possível atualizar sua senha.'));
    } finally {
      setIsChangingPassword(false);
    }
  };

  const updateMfa = async (enabled: boolean) => {
    setIsUpdatingMfa(true);
    setMfaError('');
    setMfaMessage('');
    try {
      const updatedProfile = await accountService.setEmailMfaEnabled(enabled);
      setEmailMfaEnabled(updatedProfile.emailMfaEnabled);
      setMfaMessage(enabled ? 'Código por e-mail ativado para os próximos acessos.' : 'Código por e-mail desativado.');
    } catch (error: unknown) {
      setMfaError(getApiErrorMessage(error, 'Não foi possível atualizar esta opção. Tente novamente.'));
    } finally {
      setIsUpdatingMfa(false);
    }
  };

  const initial = (user.name || user.email || '?').trim().charAt(0).toUpperCase();
  const navItems = [
    { href: '#dados', label: 'Meus dados' },
    { href: '#enderecos', label: 'Endereços' },
    { href: '#seguranca', label: 'Segurança' },
    { href: '#pedidos', label: 'Pedidos e compras' },
  ];

  return (
    <div className="min-h-screen bg-gray-100 pt-28 pb-20 text-gray-900">
      <div className="mx-auto max-w-6xl space-y-8 px-4">
        <header className="flex flex-wrap items-center justify-between gap-5 rounded-2xl bg-white p-6 shadow-sm ring-1 ring-gray-200/70">
          <div className="flex items-center gap-4">
            <div aria-hidden className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-red-primary text-2xl font-black text-white">{initial}</div>
            <div className="space-y-1.5">
              <p className="text-sm font-bold text-brand-red-primary">Minha conta</p>
              <h1 className="text-2xl font-black leading-tight sm:text-3xl">Olá, {user.name}</h1>
            </div>
          </div>
          {user.role === 'ADMIN' && <Link href="/admin" className="rounded-lg bg-brand-red-primary px-5 py-3 font-bold text-white shadow-sm transition hover:bg-red-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-red-primary">Acessar painel admin</Link>}
        </header>
        {message && <p role={messageIsError ? 'alert' : 'status'} className={`rounded-lg border p-3 text-sm ${messageIsError ? 'border-red-200 bg-red-50 text-red-800' : 'border-green-200 bg-green-50 text-green-800'}`}>{message}</p>}
        <div className="grid gap-8 lg:grid-cols-[14rem_1fr]">
          <nav aria-label="Seções da conta" className="lg:sticky lg:top-28 lg:self-start">
            <ul className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible lg:pb-0">
              {navItems.map(item => (
                <li key={item.href} className="shrink-0">
                  <a href={item.href} className="block rounded-lg border-l-4 border-transparent px-4 py-2.5 text-sm font-semibold text-gray-600 transition hover:border-brand-red-primary hover:bg-white hover:text-brand-red-primary focus-visible:outline-2 focus-visible:outline-brand-red-primary">{item.label}</a>
                </li>
              ))}
            </ul>
          </nav>
          <div className="space-y-6">
            <section id="dados" className={cardClass}>
              <h2 className={cardTitleClass}>Meus dados</h2>
              <form onSubmit={saveProfile} className="space-y-4">
                <input value={profile.name} onChange={e => setProfile({ ...profile, name: e.target.value })} placeholder="Nome completo" aria-label="Nome completo" autoComplete="name" maxLength={120} className={inputClass} required />
                <input value={user.email} readOnly aria-label="E-mail (não editável)" className={`${inputClass} bg-gray-50 text-gray-500`} />
                <input value={profile.phone} onChange={e => setProfile({ ...profile, phone: formatPhone(e.target.value) })} placeholder="Telefone com DDD" aria-label="Telefone" type="tel" inputMode="tel" autoComplete="tel" maxLength={15} className={inputClass} />
                <button disabled={isSavingProfile} className={primaryButtonClass}>{isSavingProfile ? 'Salvando...' : 'Salvar dados'}</button>
              </form>
            </section>
            <section id="enderecos" className={cardClass}>
              <div className="mb-5 flex items-center justify-between gap-3"><h2 className="text-xl font-bold">Endereços</h2><button type="button" onClick={() => setShowAddress(!showAddress)} aria-expanded={showAddress} className="rounded-lg border border-brand-red-primary px-4 py-1.5 text-sm font-bold text-brand-red-primary transition hover:bg-brand-red-primary hover:text-white">{showAddress ? 'Fechar' : 'Adicionar'}</button></div>
              {showAddress && <form onSubmit={saveAddress} className="mb-5 grid grid-cols-2 gap-3 rounded-xl bg-gray-50 p-4">
                {ADDRESS_FIELDS.map(({ field, label, placeholder, required }) => <input key={field} value={address[field] || ''} onChange={e => setAddress({ ...address, [field]: field === 'zipCode' ? formatCep(e.target.value) : field === 'state' ? e.target.value.toUpperCase().slice(0, 2) : e.target.value })} placeholder={placeholder} aria-label={label} maxLength={field === 'zipCode' ? 9 : undefined} className={`${inputClass} text-sm first:col-span-2`} required={required} />)}
                <button disabled={isSavingAddress} className={`${primaryButtonClass} col-span-2`}>{isSavingAddress ? 'Salvando...' : 'Salvar endereço'}</button>
              </form>}
              {addresses.length === 0 ? <p className="rounded-xl border border-dashed border-gray-300 p-5 text-center text-sm text-gray-500">Nenhum endereço salvo. Adicione um para agilizar suas compras.</p> : <div className="space-y-3">{addresses.map(item => <div key={item.id} className="flex items-start justify-between gap-4 rounded-xl border border-gray-200 p-4 text-sm"><span className="flex items-start gap-3"><MapPin className="mt-0.5 h-4 w-4 shrink-0 text-brand-red-primary" aria-hidden /><span>{item.street}, {item.number} - {item.city}/{item.state}<br /><span className="text-gray-500">CEP {item.zipCode}</span></span></span><button type="button" onClick={() => removeAddress(item)} className="font-semibold text-brand-red-primary hover:underline">Excluir</button></div>)}</div>}
            </section>
            <section id="seguranca" className={cardClass}>
              <h2 className={cardTitleClass}>Segurança</h2>
              <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-xl bg-gray-50 p-4">
                <div>
                  <h3 className="font-semibold">Código por e-mail no login</h3>
                  <p className="text-sm text-gray-600">Solicita uma segunda etapa após a senha.</p>
                  {mfaError && <p role="alert" className="mt-2 text-sm text-red-700">{mfaError}</p>}
                  {mfaMessage && <p role="status" className="mt-2 text-sm text-green-700">{mfaMessage}</p>}
                </div>
                <label className="flex items-center gap-3 text-sm font-medium">
                  <span>{emailMfaEnabled ? 'Ativado' : 'Desativado'}</span>
                  <input type="checkbox" checked={emailMfaEnabled} disabled={isUpdatingMfa} onChange={event => updateMfa(event.target.checked)} className="h-5 w-5 accent-brand-red-primary" aria-label="Ativar código de segurança por e-mail no login" />
                </label>
              </div>
              <p className="mb-4 text-sm text-gray-600">Altere sua senha. Use pelo menos 8 caracteres, com letras e números.</p>
              <form onSubmit={changePassword} className="grid gap-3 sm:grid-cols-2">
                <input type="password" autoComplete="current-password" value={passwordForm.currentPassword} onChange={event => setPasswordForm({ ...passwordForm, currentPassword: event.target.value })} placeholder="Senha atual" aria-label="Senha atual" className={inputClass} required />
                <span className="hidden sm:block" />
                <input type="password" autoComplete="new-password" minLength={8} maxLength={128} value={passwordForm.newPassword} onChange={event => setPasswordForm({ ...passwordForm, newPassword: event.target.value })} placeholder="Nova senha" aria-label="Nova senha" className={inputClass} required />
                <input type="password" autoComplete="new-password" minLength={8} maxLength={128} value={passwordForm.confirmPassword} onChange={event => setPasswordForm({ ...passwordForm, confirmPassword: event.target.value })} placeholder="Confirme a nova senha" aria-label="Confirme a nova senha" className={inputClass} required />
                {passwordError && <p role="alert" className="text-sm text-red-700 sm:col-span-2">{passwordError}</p>}
                {passwordMessage && <p role="status" className="text-sm text-green-700 sm:col-span-2">{passwordMessage}</p>}
                <button disabled={isChangingPassword} className={`${primaryButtonClass} sm:col-span-2`}>
                  {isChangingPassword ? 'Atualizando...' : 'Atualizar senha'}
                </button>
              </form>
            </section>
            <section id="pedidos" className={cardClass}>
              <h2 className={cardTitleClass}>Pedidos e compras</h2>
              {orders.length === 0 ? <p className="rounded-xl border border-dashed border-gray-300 p-5 text-center text-sm text-gray-500">Você ainda não possui compras.</p> : <div className="space-y-3">{orders.map(order => <div key={order.id} className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-gray-200 p-4"><span className="font-semibold">Pedido #{order.id.slice(0, 8)}<br /><small className="font-normal text-gray-500">{new Date(order.createdAt).toLocaleDateString('pt-BR')}</small></span><span className="rounded-full bg-red-50 px-3 py-1 text-sm font-semibold text-brand-red-primary">{orderStatusLabel(order.status)}</span><span className="font-bold">R$ {Number(order.total).toFixed(2).replace('.', ',')}</span></div>)}</div>}
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}
