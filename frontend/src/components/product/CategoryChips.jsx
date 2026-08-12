import './CategoryChips.css';

export default function CategoryChips({ categories, activeCategory, onSelectCategory }) {
  return (
    <div className="category-chips-container">
      {categories.map((category) => (
        <button
          key={category.id}
          className={`chip ${activeCategory === category.id ? 'chip-active' : ''}`}
          onClick={() => onSelectCategory(category.id)}
        >
          {category.name}
        </button>
      ))}
    </div>
  );
}
