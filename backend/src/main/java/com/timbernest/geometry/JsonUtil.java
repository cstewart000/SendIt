package com.timbernest.geometry;

import tools.jackson.databind.ObjectMapper;
import com.timbernest.common.ApiException;
import com.timbernest.geometry.model.GeometryModel;
import com.timbernest.geometry.model.GeoIssue;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class JsonUtil {
    private final ObjectMapper mapper;

    public JsonUtil(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String toJson(Object o) {
        try { return mapper.writeValueAsString(o); }
        catch (Exception e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage()); }
    }

    public GeometryModel toModel(String json) {
        try { return mapper.readValue(json, GeometryModel.class); }
        catch (Exception e) { throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid geometry JSON"); }
    }

    public List<GeoIssue> toIssues(String json) {
        try { return Arrays.asList(mapper.readValue(json, GeoIssue[].class)); }
        catch (Exception e) { return List.of(); }
    }
}
