import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import MainLayout from '../../components/layout/MainLayout';
import ProductCard from '../../components/product/ProductCard';
import CategoryChips from '../../components/product/CategoryChips';
import Pagination from '../../components/ui/Pagination';
import productApi from '../../api/productApi';
import categoryApi from '../../api/categoryApi';
import './ProductList.css';

export default function ProductList() {
  const [products, setProducts] = useState([]);
  const [categories, setCategories] = useState([{ id: '', name: 'Tất cả sản phẩm' }]);
  
  // Các state để fetch API
  const [searchParams, setSearchParams] = useSearchParams();
  const activeCategoryId = searchParams.get('category') || '';
  const urlPage = parseInt(searchParams.get('page') || '1', 10);
  const currentPage = urlPage > 0 ? urlPage - 1 : 0;

  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  // 1. Fetch Categories 1 lần khi load trang
  useEffect(() => {
    const fetchCategories = async () => {
      try {
        const data = await categoryApi.getAllCategories();
        // Gắn thêm 'Tất cả' lên đầu danh sách trả về từ backend
        setCategories([{ id: '', name: 'Tất cả sản phẩm' }, ...data]);
      } catch (error) {
        console.error('Lỗi khi tải danh mục', error);
      }
    };
    fetchCategories();
  }, []);

  // 2. Fetch Products mỗi khi page hoặc category thay đổi
  useEffect(() => {
    const fetchProducts = async () => {
      setIsLoading(true);
      try {
        // Gửi param xuống Spring Boot backend
        const params = {
          page: currentPage,
          size: 8, // Hiển thị 8 sp 1 trang
          ...(activeCategoryId ? { categoryId: activeCategoryId } : {})
        };
        
        const response = await productApi.getProducts(params);
        // Spring Boot PageResponse trả về content, totalPages...
        setProducts(response.content);
        setTotalPages(response.totalPages);
      } catch (error) {
        console.error('Lỗi khi tải sản phẩm', error);
      } finally {
        setIsLoading(false);
      }
    };
    
    fetchProducts();
  }, [activeCategoryId, currentPage]); // Gọi lại API khi 2 dependencies này thay đổi

  // Handler khi click chọn category khác
  const handleCategorySelect = (categoryId) => {
    const params = new URLSearchParams(searchParams);
    if (categoryId) {
      params.set('category', categoryId.toString());
    } else {
      params.delete('category');
    }
    params.set('page', '1'); // Reset về trang 1
    setSearchParams(params);
  };

  const handlePageChange = (newPage) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', (newPage + 1).toString());
    setSearchParams(params);
  };

  return (
    <MainLayout>
      <div className="product-list-page">
        <section className="hero-section">
          <div className="hero-content">
            <h1 className="hero-title">Khám phá Năng lượng Kinetic</h1>
            <p className="hero-subtitle">Bộ sưu tập giày thể thao mới nhất dành cho bạn.</p>
          </div>
        </section>

        <div className="container">
          <div className="filter-section">
            <CategoryChips 
              categories={categories}
              activeCategory={activeCategoryId}
              onSelectCategory={handleCategorySelect}
            />
          </div>

          {isLoading ? (
            <div style={{ textAlign: 'center', padding: '50px' }}>Đang tải sản phẩm...</div>
          ) : (
            <>
              <div className="product-grid">
                {products.map(product => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
              
              {products.length === 0 && (
                <div className="empty-state">
                  Không tìm thấy sản phẩm nào trong danh mục này.
                </div>
              )}

              {/* Hiện nút phân trang */}
              <Pagination 
                currentPage={currentPage}
                totalPages={totalPages}
                onPageChange={handlePageChange}
              />
            </>
          )}
        </div>
      </div>
    </MainLayout>
  );
}
