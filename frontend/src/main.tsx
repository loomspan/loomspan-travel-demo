import React from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';

function App() {
  return <main className="shell">
    <p className="eyebrow">DETOUR</p>
    <h1>A fresh travel platform foundation.</h1>
    <p className="lede">DeTour is ready for the next stage of development.</p>
    <p className="note">This initial platform contains no trip, catalog, or booking experience yet.</p>
  </main>;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><App /></React.StrictMode>);
