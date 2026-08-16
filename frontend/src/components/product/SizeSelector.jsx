import './SizeSelector.css';

export default function SizeSelector({ variants, selectedSize, onSelectSize }) {
  if (!variants || variants.length === 0) return null;

  return (
    <div className="size-selector-container">
      <div className="size-selector-header">
        <span className="size-selector-label">Chọn Kích cỡ</span>
      </div>
      
      <div className="size-grid">
        {variants.map((variant) => {
          const isDisabled = variant.stock === 0;
          return (
            <button
              key={variant.id || variant.size}
              className={`size-btn ${selectedSize === variant.size ? 'size-btn-active' : ''} ${isDisabled ? 'size-btn-disabled' : ''}`}
              onClick={() => !isDisabled && onSelectSize(variant.size)}
              disabled={isDisabled}
              title={isDisabled ? 'Hết hàng' : `Còn ${variant.stock} sản phẩm`}
            >
              {variant.size}
            </button>
          );
        })}
      </div>
    </div>
  );
}
