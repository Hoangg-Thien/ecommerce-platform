import { useState } from 'react';
import MainLayout from '../components/layout/MainLayout';
import ProductCard from '../components/product/ProductCard';
import CategoryChips from '../components/product/CategoryChips';
import './ProductList.css';

// Dữ liệu giả lập cho sản phẩm
const MOCK_PRODUCTS = [
  {
    id: 1,
    name: 'Áo Khoác Nam Thể Thao Đa Năng',
    price: 450000,
    image: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?q=80&w=600&auto=format&fit=crop',
    categoryId: 'men',
    badge: { type: 'new', text: 'Mới' }
  },
  {
    id: 2,
    name: 'Giày Chạy Bộ Nam Siêu Nhẹ',
    price: 1200000,
    image: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?q=80&w=600&auto=format&fit=crop',
    categoryId: 'shoes',
    badge: { type: 'sale', text: 'Giảm 20%' }
  },
  {
    id: 3,
    name: 'Balo Du Lịch Chống Nước',
    price: 650000,
    image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?q=80&w=600&auto=format&fit=crop',
    categoryId: 'accessories',
    badge: null
  },
  {
    id: 4,
    name: 'Áo Thun Nữ Cotton Hữu Cơ',
    price: 250000,
    image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?q=80&w=600&auto=format&fit=crop',
    categoryId: 'women',
    badge: { type: 'stock', text: 'Sắp hết' }
  },
  {
    id: 5,
    name: 'Quần Jeans Nam Phom Chuẩn',
    price: 550000,
    image: 'https://images.unsplash.com/photo-1541099649105-f69ad21f3246?q=80&w=600&auto=format&fit=crop',
    categoryId: 'men',
    badge: null
  },
  {
    id: 6,
    name: 'Đồng Hồ Thời Trang Nam Nữ',
    price: 1850000,
    image: 'https://images.unsplash.com/photo-1524592094714-0f0654e20314?q=80&w=600&auto=format&fit=crop',
    categoryId: 'accessories',
    badge: { type: 'new', text: 'Mới' }
  },
  {
    id: 7,
    name: 'Kính Mát Chống Tia UV',
    price: 320000,
    image: 'https://images.unsplash.com/photo-1511499767150-a48a237f0083?q=80&w=600&auto=format&fit=crop',
    categoryId: 'accessories',
    badge: null
  },
  {
    id: 8,
    name: 'Váy Nữ Mùa Hè Thoáng Mát',
    price: 480000,
    image: 'https://images.unsplash.com/photo-1515347619252-78fa8cb79622?q=80&w=600&auto=format&fit=crop',
    categoryId: 'women',
    badge: { type: 'sale', text: 'Sale Cuối Tuần' }
  }
];

const CATEGORIES = [
  { id: 'all', name: 'Tất cả sản phẩm' },
  { id: 'men', name: 'Thời trang Nam' },
  { id: 'women', name: 'Thời trang Nữ' },
  { id: 'shoes', name: 'Giày dép' },
  { id: 'accessories', name: 'Phụ kiện' }
];

export default function ProductList() {
  const [activeCategory, setActiveCategory] = useState('all');

  const filteredProducts = activeCategory === 'all' 
    ? MOCK_PRODUCTS 
    : MOCK_PRODUCTS.filter(p => p.categoryId === activeCategory);

  return (
    <MainLayout>
      <div className="product-list-page">
        <section className="hero-section">
          <div className="hero-content">
            <h1 className="hero-title">Khám phá Năng lượng Kinetic</h1>
            <p className="hero-subtitle">Bộ sưu tập thời trang công nghệ mới nhất dành cho bạn.</p>
          </div>
        </section>

        <div className="container">
          <div className="filter-section">
            <CategoryChips 
              categories={CATEGORIES}
              activeCategory={activeCategory}
              onSelectCategory={setActiveCategory}
            />
          </div>

          <div className="product-grid">
            {filteredProducts.map(product => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
          
          {filteredProducts.length === 0 && (
            <div className="empty-state">
              Không tìm thấy sản phẩm nào trong danh mục này.
            </div>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
