/*
 * Copyright 2017 Huawei Technologies Co., Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.servicecomb.transport.rest.client.http;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response.Status;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JavaType;

import io.servicecomb.common.rest.RestConst;
import io.servicecomb.common.rest.codec.produce.ProduceProcessor;
import io.servicecomb.common.rest.definition.RestOperationMeta;
import io.servicecomb.core.Invocation;
import io.servicecomb.core.definition.OperationMeta;
import io.servicecomb.foundation.vertx.http.HttpServletResponseEx;
import io.servicecomb.swagger.invocation.Response;
import io.servicecomb.swagger.invocation.exception.CommonExceptionData;
import io.servicecomb.swagger.invocation.exception.InvocationException;
import io.servicecomb.swagger.invocation.response.ResponseMeta;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import mockit.Expectations;
import mockit.Mocked;

public class TestDefaultHttpClientFilter {
  private DefaultHttpClientFilter filter = new DefaultHttpClientFilter();

  @Test
  public void testOrder() {
    Assert.assertEquals(Integer.MAX_VALUE, filter.getOrder());
  }

  @Test
  public void testFindProduceProcessorNullContentType(@Mocked RestOperationMeta restOperation,
      @Mocked HttpServletResponseEx responseEx) {
    new Expectations() {
      {
        responseEx.getHeader(HttpHeaders.CONTENT_TYPE);
        result = null;
      }
    };

    Assert.assertNull(filter.findProduceProcessor(restOperation, responseEx));
  }

  @Test
  public void testFindProduceProcessorJson(@Mocked RestOperationMeta restOperation,
      @Mocked HttpServletResponseEx responseEx, @Mocked ProduceProcessor produceProcessor) {
    new Expectations() {
      {
        responseEx.getHeader(HttpHeaders.CONTENT_TYPE);
        result = "json";
        restOperation.findProduceProcessor("json");
        result = produceProcessor;
      }
    };

    Assert.assertSame(produceProcessor, filter.findProduceProcessor(restOperation, responseEx));
  }

  @Test
  public void testFindProduceProcessorJsonWithCharset(@Mocked RestOperationMeta restOperation,
      @Mocked HttpServletResponseEx responseEx, @Mocked ProduceProcessor produceProcessor) {
    new Expectations() {
      {
        responseEx.getHeader(HttpHeaders.CONTENT_TYPE);
        result = "json; UTF-8";
        restOperation.findProduceProcessor("json");
        result = produceProcessor;
      }
    };

    Assert.assertSame(produceProcessor, filter.findProduceProcessor(restOperation, responseEx));
  }

  @Test
  public void testAfterReceiveResponseNullProduceProcessor(@Mocked Invocation invocation,
      @Mocked HttpServletResponseEx responseEx,
      @Mocked OperationMeta operationMeta,
      @Mocked RestOperationMeta swaggerRestOperation) throws Exception {
    new Expectations() {
      {
        invocation.getOperationMeta();
        result = operationMeta;
        operationMeta.getExtData(RestConst.SWAGGER_REST_OPERATION);
        result = swaggerRestOperation;
      }
    };

    Response response = filter.afterReceiveResponse(invocation, responseEx);
    InvocationException exception = response.getResult();
    CommonExceptionData data = (CommonExceptionData) exception.getErrorData();
    Assert.assertEquals("path null, statusCode 0, reasonPhrase null, response content-type null is not supported",
        data.getMessage());
  }

  @Test
  public void testAfterReceiveResponseNormal(@Mocked Invocation invocation,
      @Mocked HttpServletResponseEx responseEx,
      @Mocked Buffer bodyBuffer,
      @Mocked OperationMeta operationMeta,
      @Mocked ResponseMeta responseMeta,
      @Mocked RestOperationMeta swaggerRestOperation,
      @Mocked ProduceProcessor produceProcessor) throws Exception {
    Map<String, JavaType> headerMeta = new HashMap<>();
    headerMeta.put("a", null);
    headerMeta.put("b", null);

    MultiMap responseHeader = new CaseInsensitiveHeaders();
    responseHeader.add("b", "bValue");

    Object decodedResult = new Object();
    new Expectations() {
      {
        responseMeta.getHeaders();
        result = headerMeta;
        responseEx.getHeader(HttpHeaders.CONTENT_TYPE);
        result = "json";
        responseEx.getHeaders("b");
        result = responseHeader.getAll("b");
        swaggerRestOperation.findProduceProcessor("json");
        result = produceProcessor;
        produceProcessor.decodeResponse(bodyBuffer, responseMeta.getJavaType());
        result = decodedResult;

        invocation.getOperationMeta();
        result = operationMeta;
        operationMeta.getExtData(RestConst.SWAGGER_REST_OPERATION);
        result = swaggerRestOperation;

        responseEx.getStatusType();
        result = Status.OK;
      }
    };

    Response response = filter.afterReceiveResponse(invocation, responseEx);
    Assert.assertSame(decodedResult, response.getResult());
    Assert.assertEquals(1, response.getHeaders().getHeaderMap().size());
    Assert.assertEquals(response.getHeaders().getHeader("b"), Arrays.asList("bValue"));
  }
}
