package io.quarkus.tensorflow;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ObjectDetectionResourceTest {

    @Test
    public void testHelloEndpoint() {
        given()
          .when().get("/detect?image=https://d17fnq9dkz9hgj.cloudfront.net/uploads/2012/11/153558006-tips-healthy-cat-632x475.jpg")
          .then()
             .statusCode(200);
    }

}