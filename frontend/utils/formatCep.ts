export const formatCep = (value: string) => {
  const digits = value
    .replace(/\D/g, "")
    .slice(0, 8);

  return digits.replace(
    /^(\d{5})(\d{0,3})$/,
    (_, first, second) =>
      second ? `${first}-${second}` : first
  );
};