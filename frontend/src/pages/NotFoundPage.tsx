import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <div className="grid min-h-dvh place-items-center px-6">
      <div className="text-center">
        <span className="stamp text-sm">Page not found</span>
        <h1 className="mt-6 text-2xl font-semibold">This page isn't in the passbook</h1>
        <p className="mt-2 text-ink-soft">The link may be old, or the address mistyped.</p>
        <Link to="/" className="mt-6 inline-flex h-10 items-center rounded-lg bg-kosh-800 px-4 font-medium text-paper hover:bg-kosh-700">
          Go to my home page
        </Link>
      </div>
    </div>
  );
}
