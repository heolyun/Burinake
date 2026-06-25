type Props = {
  label: string;
  value: number | string;
  tone?: 'default' | 'warning' | 'danger';
};

export function SummaryCard({ label, value, tone = 'default' }: Props) {
  return (
    <article className={`summary-card summary-${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}
