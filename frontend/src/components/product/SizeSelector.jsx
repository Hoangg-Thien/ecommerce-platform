import './SizeSelector.css';

export default function SizeSelector({ sizes, selectedSize, onSelectSize }) {
  return (
    <div className="size-selector-container">
      <div className="size-selector-header">
        <span className="size-selector-label">Chọn Kích cỡ</span>
        <button className="size-guide-btn">Bảng size</button>
      </div>
      
      <div className="size-grid">
        {sizes.map((size) => (
          <button
            key={size}
            className={`size-btn ${selectedSize === size ? 'size-btn-active' : ''}`}
            onClick={() => onSelectSize(size)}
          >
            {size}
          </button>
        ))}
      </div>
    </div>
  );
}
