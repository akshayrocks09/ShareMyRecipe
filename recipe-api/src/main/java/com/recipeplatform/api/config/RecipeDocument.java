package com.recipeplatform.api.config;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Document(indexName = "recipes")
@Setting(settingPath = "elasticsearch/recipe-settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeDocument {

    @Id
    private String id;  // recipe UUID as string

    @Field(type = FieldType.Text, analyzer = "english")
    private String title;

    @Field(type = FieldType.Text, analyzer = "english")
    private String summary;

    @Field(type = FieldType.Text, analyzer = "english")
    private String ingredientsText;   // flattened ingredient names

    @Field(type = FieldType.Text, analyzer = "english")
    private String stepsText;         // flattened step instructions

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
