import Header from './Header';
import './MainLayout.css';

export default function MainLayout({ children }) {
  return (
    <div className="main-layout">
      <Header />
      <main className="main-content">
        {children}
      </main>
      <footer className="main-footer">
        <div className="footer-container">
          <p>© 2026 XOÀI. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
