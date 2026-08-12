import { useState } from 'react';
import { ChevronDown } from 'lucide-react';
import './Accordion.css';

export default function Accordion({ title, children, defaultOpen = false }) {
  const [isOpen, setIsOpen] = useState(defaultOpen);

  return (
    <div className="accordion">
      <button 
        className="accordion-header" 
        onClick={() => setIsOpen(!isOpen)}
        aria-expanded={isOpen}
      >
        <span className="accordion-title">{title}</span>
        <ChevronDown 
          size={20} 
          className={`accordion-icon ${isOpen ? 'accordion-icon-open' : ''}`} 
        />
      </button>
      
      <div className={`accordion-content-wrapper ${isOpen ? 'accordion-content-wrapper--open' : ''}`}>
        <div className="accordion-content">
          <div className="accordion-content-inner">
            {children}
          </div>
        </div>
      </div>
    </div>
  );
}
