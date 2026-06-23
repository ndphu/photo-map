import type { ReactNode } from "react";

interface PagePanelProps {
  title: string;
  children: ReactNode;
}

export function PagePanel({ title, children }: PagePanelProps) {
  return (
    <section className="page-panel">
      <h1>{title}</h1>
      <div>{children}</div>
    </section>
  );
}
