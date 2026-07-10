type Props = {
  level: number | null;
};

export function IssueLevelBadge({ level }: Props) {
  if (!level) return <span className="badge level-empty">미판단</span>;
  return <span className={`badge level-${level}`}>Level {level}</span>;
}
