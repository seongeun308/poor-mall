package io.github.seongeun308.poormall.global.error;

import io.github.seongeun308.poormall.PoorMallApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    private static final String BASE_PACKAGE = PoorMallApplication.class.getPackageName();

    @Test
    @DisplayName("에러 코드는 모든 ErrorCode 구현체에 걸쳐 중복되지 않는다")
    void errorCodesShouldBeUnique() {
        List<ErrorCode> all = findAllErrorCodes();

        Map<String, List<String>> duplicates = all.stream()
                .collect(Collectors.groupingBy(
                        ErrorCode::getCode,
                        Collectors.mapping(this::describe, Collectors.toList())))
                .entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        assertThat(duplicates)
                .as("중복된 에러 코드가 있습니다: %s", duplicates)
                .isEmpty();
    }

    @Test
    @DisplayName("프로젝트에서 ErrorCode 구현체를 하나 이상 찾을 수 있다")
    void shouldFindErrorCodeImplementations() {
        assertThat(findAllErrorCodes()).isNotEmpty();
    }

    private List<ErrorCode> findAllErrorCodes() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                        return beanDefinition.getMetadata().isIndependent();  // enum 포함 허용
                    }
                };
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCode.class));

        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(bd -> loadClass(bd.getBeanClassName()))
                .filter(Class::isEnum)
                .flatMap(clazz -> Arrays.stream(clazz.getEnumConstants()))
                .map(ErrorCode.class::cast)
                .toList();
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private String describe(ErrorCode code) {
        return ((Enum<?>) code).getDeclaringClass().getSimpleName() + "." + ((Enum<?>) code).name();
    }

}