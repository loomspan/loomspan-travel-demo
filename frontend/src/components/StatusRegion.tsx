type StatusRegionProps = {message?: string};

export function StatusRegion({message}: StatusRegionProps) {
  if (!message) return null;
  return <div className="status" role="status" aria-atomic="true">{message}</div>;
}
