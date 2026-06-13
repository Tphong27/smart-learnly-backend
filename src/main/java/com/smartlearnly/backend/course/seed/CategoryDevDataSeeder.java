package com.smartlearnly.backend.course.seed;

import com.smartlearnly.backend.course.entity.Category;
import com.smartlearnly.backend.course.repository.CategoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "categories-enabled", havingValue = "true")
public class CategoryDevDataSeeder implements ApplicationRunner {
    private static final List<CategorySeed> CATEGORY_SEEDS = List.of(
            new CategorySeed("Cloud Computing", "cloud-computing", 10),
            new CategorySeed("Software Development", "software-development", 20),
            new CategorySeed("Data & AI", "data-ai", 30)
    );

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        CATEGORY_SEEDS.forEach(this::seed);
    }

    private void seed(CategorySeed seed) {
        if (categoryRepository.existsBySlugIgnoreCase(seed.slug())) {
            return;
        }
        Category category = new Category();
        category.setName(seed.name());
        category.setSlug(seed.slug());
        category.setActive(true);
        category.setSortOrder(seed.sortOrder());
        categoryRepository.save(category);
    }

    private record CategorySeed(String name, String slug, int sortOrder) {
    }
}
