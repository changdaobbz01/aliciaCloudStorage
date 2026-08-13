package com.alicia.cloudstorage.rag.assistant;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NamePredicateParserTest {

    @Test
    void normalizesColloquialContainsOperatorsIntoTheSamePredicate() {
        Map<String, String> cases = Map.of(
                "删除名称带测试的文件", "FILE",
                "删除名称带有测试的文件", "FILE",
                "删除名称含测试的文件", "FILE",
                "删除名称包含测试的文件", "FILE",
                "删除名称包含有测试的文件", "FILE",
                "删除名字里有测试的文件", "FILE",
                "删除名字带着测试的文件", "FILE",
                "删除文件名中带测试的文件", "FILE",
                "删除名称带测试的文件夹", "FOLDER",
                "删除名称带测试的文件或文件夹", "ANY"
        );

        cases.forEach((message, resultType) -> {
            NamePredicateParser.NamePredicate predicate = NamePredicateParser.parse(message).orElseThrow();

            assertThat(predicate.value()).as(message).isEqualTo("测试");
            assertThat(predicate.resultType()).as(message).isEqualTo(resultType);
        });
    }

    @Test
    void doesNotTreatAnExactNameLabelAsACollectionPredicate() {
        assertThat(NamePredicateParser.parse("删除名为名称带测试的文件")).isEmpty();
    }

    @Test
    void preservesSpacesAndParticlesInsideQuotedPredicateValues() {
        NamePredicateParser.NamePredicate predicate = NamePredicateParser
                .parse("删除名称包含“我的 项目”的文件")
                .orElseThrow();

        assertThat(predicate.value()).isEqualTo("我的 项目");
        assertThat(predicate.resultType()).isEqualTo("FILE");
    }
}
