package com.recipeplatform.worker.dto;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;

@Document(indexName = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "english")
    private String title;

    @Field(type = FieldType.Text, analyzer = "english")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "english")
    private String ingredientsText;

    @Field(type = FieldType.Text, analyzer = "english")
    private String stepsText;

    @Field(type = FieldType.Keyword)
    private List<String> labels;

    @Field(type = FieldType.Keyword)
    private String chefId;

    @Field(type = FieldType.Keyword)
    private String chefHandle;

    @Field(type = FieldType.Text)
    private String chefDisplayName;

    @Field(type = FieldType.Date)
    private Instant publishedAt;

    @Field(type = FieldType.Keyword)
    private String thumbUrl;
}
