package com.ecommerce.config;

import com.ecommerce.entity.Category;
import com.ecommerce.entity.Product;
import com.ecommerce.entity.ProductVariant;
import com.ecommerce.repository.CategoryRepository;
import com.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // Use a fixed seed so random stock generation is deterministic
    private final Random random = new Random(12345);

    @Override
    public void run(String... args) throws Exception {
        seedData();
    }

    @Transactional
    public void seedData() {
        log.info(">>> Starting Category and Product seeding...");

        Category football = seedCategory("Bóng đá", "Các loại giày đá bóng");
        Category basketball = seedCategory("Bóng rổ", "Các loại giày bóng rổ");
        Category volleyball = seedCategory("Bóng chuyền", "Các loại giày bóng chuyền");
        Category badminton = seedCategory("Cầu lông", "Các loại giày cầu lông");
        Category pickleball = seedCategory("Pickleball", "Các loại giày pickleball");

        seedProductsForCategory(football, "Bóng đá", 12, 700000, 2500000, new String[][]{
                {"Mercurial Vapor Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037573/MercurialVaporPro.jpg"},
                {"Phantom GX2 Elite", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037574/PhantomGX2Elite.webp"},
                {"Nike Tiempo Legend X Academy", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037574/NikeTiempoLegendXAcademy.jpg"},
                {"Predator Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037574/PredatorPro.jpg"},
                {"X Speedportal .3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037576/XSpeedportal3.jpg"},
                {"Copa Pure 2 Elite", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037573/CopaPure2Elite.jpg"},
                {"Future Ultimate", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037573/FutureUltimate.webp"},
                {"Ultra 6 Match", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037574/Ultra6Match.webp"},
                {"Ultra Ultimate", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037575/UltraUltimate.webp"},
                {"Morelia Neo III", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037574/MoreliaNeoIII.jpg"},
                {"Alpha III Elite", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037573/AlphaIIIElite.jpg"},
                {"Monarcida Neo Sala Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037573/MonarcidaNeoSalaPro.webp"}
        });

        seedProductsForCategory(basketball, "Bóng rổ", 10, 900000, 3000000, new String[][]{
                {"Court Vision", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037613/CourtVision.jpg"},
                {"Giannis Immortality 3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/GiannisImmortality3.jpg"},
                {"LeBron Witness 7 EP", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/LeBronWitness7EP.jpb.webp"},
                {"Harden Stepback 3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/HardenStepback3.webp"},
                {"Dame Certified 3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787038220/DameCertified3.webp"},
                {"Trae Unlimited 3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787038090/TraeUnlimited3.webp"},
                {"KT Splash 5", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/KTSplash5.webp"},
                {"Shock The Game 4", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/ShockTheGame4.webp"},
                {"Taichi Flash 5", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/TaichiFlash5.webp"},
                {"Lou Williams Street Master", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037614/LouWilliamsStreetMaster.webp"}
        });

        seedProductsForCategory(volleyball, "Bóng chuyền", 12, 800000, 2500000, new String[][]{
                {"Sky Elite FF MT3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037676/SkyEliteFFMT3.webp"},
                {"Netburner Ballistic FF MT3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037675/NetburnerBallisticFFMT3.webp"},
                {"Upcourt Pro 5", "https://res.cloudinary.com/auogpfp8/image/upload/v1787038030/UpcourtPro5.webp"},
                {"Wave Lightning Z6", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037678/WaveLightningZ6.png"},
                {"Wave Momentum Elite Mid", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037920/WaveMomentumEliteMid.webp"},
                {"Wave Dimension Mid", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037965/WaveDimensionMid.jpg"},
                {"Crazyflight Mid", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037674/CrazyflightMid.jpg"},
                {"Crazyflight Cross", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037673/CrazyflightCross.jpg"},
                {"Novaflight 2", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037675/Novaflight2.jpg"},
                {"Metarise Tokyo", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037674/MetariseTokyojpg.jpg"},
                {"Volley Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037677/VolleyPro.jpg"},
                {"Upcourt 6", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037676/UPCOURT6.webp"}
        });

        seedProductsForCategory(badminton, "Cầu lông", 10, 700000, 2000000, new String[][]{
                {"K-086", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037640/K-086.jpg"},
                {"K-065D", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037640/K-065D.webp"},
                {"Power Cushion 65 Z3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037641/PowerCushion65Z3.webp"},
                {"Power Cushion 65 Z4", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037641/PowerCushion65Z4.webp"},
                {"Aerus Z2", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037640/AerusZ2.webp"},
                {"Ranger Lite Se II", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037641/RangerLiteSeII.webp"},
                {"Ultra Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037641/UltraPro.webp"},
                {"Blade Pro", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037640/BladePro.webp"},
                {"A970 NitroLite A", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037640/A970NitroLiteA.webp"},
                {"P9200 Hang C", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037779/P9200HangC.jpg"}
        });

        seedProductsForCategory(pickleball, "Pickleball", 5, 800000, 2500000, new String[][]{
                {"Court Air Zoom Vapor Pro 2", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037662/CourtAirZoomVaporPro2.webp"},
                {"Vapor Lite 3", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037664/VaporLite3.jpg"},
                {"GP Challenge Court", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037664/GPChallengeCourt.webp"},
                {"Courtflash Speed 2", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037663/CourtflashSpeed2.webp"},
                {"Gel-Dedicate 8", "https://res.cloudinary.com/auogpfp8/image/upload/v1787037663/GelDedicate8.webp"}
        });

        log.info(">>> Finished Category and Product seeding.");
    }

    private Category seedCategory(String name, String description) {
        Optional<Category> existingCategory = categoryRepository.findByName(name);
        if (existingCategory.isPresent()) {
            return existingCategory.get();
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(description);
        return categoryRepository.save(category);
    }

    private void seedProductsForCategory(Category category, String categoryName, int count, int minPrice, int maxPrice, String[][] baseNames) {
        for (int i = 0; i < count; i++) {
            String productName = i < baseNames.length ? baseNames[i][0] : categoryName + " Pro Model " + (i + 1);

            // Check if product already exists to prevent duplicates
            if (productRepository.existsByName(productName)) {
                continue;
            }

            Product product = new Product();
            product.setName(productName);
            
            // Random price within range, rounded to nearest 10,000
            int randomPrice = minPrice + random.nextInt(maxPrice - minPrice + 1);
            randomPrice = (randomPrice / 10000) * 10000;
            product.setPrice(BigDecimal.valueOf(randomPrice));
            
            product.setDescription("Giày " + categoryName.toLowerCase() + " chất lượng cao, " + productName + " mang lại trải nghiệm tuyệt vời trên sân.");
            
            // Generate a simple placeholder image URL encoded properly
           String imageUrl = i < baseNames.length && baseNames[i].length > 1 
            ? baseNames[i][1] 
            : "https://via.placeholder.com/400x400.png?text=" + productName.replace(" ", "+");

            product.setImageUrl(imageUrl); 
            
            product.setCategory(category);

            // Add exactly 8 variants (sizes 39 to 46)
            List<ProductVariant> variants = new ArrayList<>();
            String[] sizes = {"39", "40", "41", "42", "43", "44", "45", "46"};
            for (String size : sizes) {
                ProductVariant variant = new ProductVariant();
                variant.setSize(size);
                variant.setProduct(product);
                
                // Deterministic stock generation based on a pattern to ensure variety
                // 0 (out of stock), 1-2 (sắp hết), 3-5 (còn ít), 6+ (còn nhiều)
                int stockPattern = random.nextInt(100);
                int stock;
                if (stockPattern < 10) {
                    stock = 0; // 10% chance of out of stock
                } else if (stockPattern < 30) {
                    stock = 1 + random.nextInt(2); // 20% chance of 1-2
                } else if (stockPattern < 60) {
                    stock = 3 + random.nextInt(3); // 30% chance of 3-5
                } else {
                    stock = 6 + random.nextInt(15); // 40% chance of 6-20
                }
                
                variant.setStock(stock);
                variants.add(variant);
            }
            
            product.setVariants(variants);
            productRepository.save(product);
        }
    }
}
