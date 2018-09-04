/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.config.hocon;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.hocon.internal.HoconConfigParser;
import io.helidon.config.spi.ConfigNode;
import io.helidon.config.spi.ConfigNode.ListNode;
import io.helidon.config.spi.ConfigNode.ObjectNode;
import io.helidon.config.spi.ConfigParser;
import io.helidon.config.spi.ConfigParser.Content;
import io.helidon.config.spi.ConfigParserException;

import com.typesafe.config.ConfigResolveOptions;
import io.helidon.common.CollectionsHelper;
import io.helidon.config.ConfigMapper;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.MissingValueException;

import static io.helidon.config.testing.ValueNodeMatcher.valueNode;
import static org.hamcrest.MatcherAssert.assertThat;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link HoconConfigParser}.
 */
public class HoconConfigParserTest {

    @Test
    public void testResolveEnabled() {
        ConfigParser parser = HoconConfigParserBuilder.buildDefault();
        ObjectNode node = parser.parse((StringContent) () -> ""
                + "aaa = 1 \n"
                + "bbb = ${aaa} \n"
                + "ccc = \"${aaa}\" \n"
                + "ddd = ${?zzz}");

        assertThat(node.entrySet(), hasSize(3));
        assertThat(node.get("aaa"), valueNode("1"));
        assertThat(node.get("bbb"), valueNode("1"));
        assertThat(node.get("ccc"), valueNode("${aaa}"));
    }

    @Test
    public void testResolveDisabled() {
        ConfigParserException cpe = Assertions.assertThrows(ConfigParserException.class, () -> {
            ConfigParser parser = HoconConfigParserBuilder.create().disableResolving().build();
            parser.parse((StringContent) () -> ""
                + "aaa = 1 \n"
                + "bbb = ${aaa} \n"
                + "ccc = \"${aaa}\" \n"
                + "ddd = ${?zzz}");
        });

        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf(
                "Cannot read from source",
                "substitution not resolved",
                "${aaa}")).matches(cpe.getMessage()));
        Assertions.assertTrue(instanceOf(com.typesafe.config.ConfigException.NotResolved.class)
                .matches(cpe.getCause()));
    }

    @Test
    public void testResolveEnabledEnvVar() {
        ConfigParser parser = HoconConfigParserBuilder.buildDefault();
        ObjectNode node = parser.parse((StringContent) () -> "env-var = ${HOCON_TEST_PROPERTY}");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("env-var"), valueNode("This Is My ENV VARS Value."));
    }

    @Test
    public void testResolveEnabledEnvVarDisabled() {
        ConfigParserException cpe = Assertions.assertThrows(ConfigParserException.class, () -> {
            ConfigParser parser = HoconConfigParserBuilder.create()
                    .resolveOptions(ConfigResolveOptions.noSystem())
                    .build();
        parser.parse((StringContent) () -> "env-var = ${HOCON_TEST_PROPERTY}");
        });

        Assertions.assertTrue(stringContainsInOrder(CollectionsHelper.listOf(
                "Cannot read from source",
                "not resolve substitution ",
                "${HOCON_TEST_PROPERTY}")).matches(cpe.getMessage()),
                "Unexpected exception message: " + cpe.getMessage());
        Assertions.assertTrue(instanceOf(com.typesafe.config.ConfigException.UnresolvedSubstitution.class)
                .matches(cpe.getCause()),
                "Unexpected exception cause type: " + cpe.getCause().getClass().getName());
    }

    @Test
    public void testEmpty() {
        HoconConfigParser parser = new HoconConfigParser();
        ObjectNode node = parser.parse((StringContent) () -> "");

        assertThat(node.entrySet(), hasSize(0));
    }

    @Test
    public void testSingleValue() {
        HoconConfigParser parser = new HoconConfigParser();
        ObjectNode node = parser.parse((StringContent) () -> "aaa = bbb");

        assertThat(node.entrySet(), hasSize(1));
        assertThat(node.get("aaa"), valueNode("bbb"));
    }

    @Test
    public void testStringListValue() {
        HoconConfigParser parser = new HoconConfigParser();
        ObjectNode node = parser.parse((StringContent) () -> "aaa = [ bbb, ccc, ddd ]");

        assertThat(node.entrySet(), hasSize(1));

        List<ConfigNode> aaa = ((ListNode) node.get("aaa"));
        assertThat(aaa, hasSize(3));
        assertThat(aaa.get(0), valueNode("bbb"));
        assertThat(aaa.get(1), valueNode("ccc"));
        assertThat(aaa.get(2), valueNode("ddd"));
    }

    @Test
    public void testComplexValue() {
        HoconConfigParser parser = new HoconConfigParser();
        ObjectNode node = parser.parse((StringContent) () -> ""
                + "aaa =  \"bbb\"\n"
                + "arr = [ bbb, 13, true, 3.14159 ] \n"
                + "obj1 = { aaa = bbb, ccc = false } \n"
                + "arr2 = [ aaa, false, { bbb = 3.14159, c = true }, { ooo { ppp { xxx = yyy }}} ]"
        );

        assertThat(node.entrySet(), hasSize(4));
        assertThat(node.get("aaa"), valueNode("bbb"));
        assertThat(((ObjectNode) node.get("obj1")).get("aaa"), valueNode("bbb"));
        assertThat(((ObjectNode) node.get("obj1")).get("ccc"), valueNode("false"));
        //arr
        List<ConfigNode> arr = ((ListNode) node.get("arr"));
        assertThat(arr, hasSize(4));
        assertThat(arr.get(0), valueNode("bbb"));
        assertThat(arr.get(1), valueNode("13"));
        assertThat(arr.get(2), valueNode("true"));
        assertThat(arr.get(3), valueNode("3.14159"));
        //arr2
        List<ConfigNode> arr2 = ((ListNode) node.get("arr2"));
        assertThat(arr2, hasSize(4));
        assertThat(arr2.get(0), valueNode("aaa"));
        assertThat(arr2.get(1), valueNode("false"));
        //arr2[2]
        final Map<String, ConfigNode> arr2_2 = ((ObjectNode) arr2.get(2));
        assertThat(arr2_2.entrySet(), hasSize(2));
        assertThat(arr2_2.get("bbb"), valueNode("3.14159"));
        assertThat(arr2_2.get("c"), valueNode("true"));
        //arr2[3]
        final Map<String, ConfigNode> arr2_3 = ((ObjectNode) arr2.get(3));
        assertThat(arr2_3.entrySet(), hasSize(1));
        assertThat(((ObjectNode) ((ObjectNode) arr2_3.get("ooo")).get("ppp")).get("xxx"), valueNode("yyy"));
    }

    /**
     * This is same test as in {@code config} module, {@code ConfigTest} class, method {@code testConfigKeyEscapedNameComplex}.
     */
    @Test
    public void testConfigKeyEscapedNameComplex() {
        String JSON = ""
                + "{\n"
                + "    \"oracle.com\": {\n"
                + "        \"prop1\": \"val1\",\n"
                + "        \"prop2\": \"val2\"\n"
                + "    },\n"
                + "    \"oracle\": {\n"
                + "        \"com\": \"1\",\n"
                + "        \"cz\": \"2\"\n"
                + "    }\n"
                + "}\n";

        Config config = Config
                .withSources(ConfigSources.from(JSON, HoconConfigParser.MEDIA_TYPE_APPLICATION_JSON))
                .addParser(new HoconConfigParser())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices()
                .build();

        //key
        assertThat(config.get("oracle~1com.prop1").asString(), is("val1"));
        assertThat(config.get("oracle~1com.prop2").asString(), is("val2"));
        assertThat(config.get("oracle.com").asString(), is("1"));
        assertThat(config.get("oracle.cz").asString(), is("2"));

        //name
        assertThat(config.get("oracle~1com").name(), is("oracle.com"));
        assertThat(config.get("oracle~1com.prop1").name(), is("prop1"));
        assertThat(config.get("oracle~1com.prop2").name(), is("prop2"));
        assertThat(config.get("oracle").name(), is("oracle"));
        assertThat(config.get("oracle.com").name(), is("com"));
        assertThat(config.get("oracle.cz").name(), is("cz"));

        //child nodes
        List<Config> children = config.asNodeList();
        assertThat(children, hasSize(2));
        assertThat(children.stream().map(Config::name).collect(Collectors.toSet()),
                   containsInAnyOrder("oracle.com", "oracle"));

        //traverse
        Set<String> keys = config.traverse().map(Config::key).map(Config.Key::toString).collect(Collectors.toSet());
        assertThat(keys, hasSize(6));
        assertThat(keys, containsInAnyOrder("oracle~1com", "oracle~1com.prop1", "oracle~1com.prop2",
                                            "oracle", "oracle.com", "oracle.cz"));

        //map
        Map<String, String> map = config.asMap();
        assertThat(map.keySet(), hasSize(4));
        assertThat(map.get("oracle~1com.prop1"), is("val1"));
        assertThat(map.get("oracle~1com.prop2"), is("val2"));
        assertThat(map.get("oracle.com"), is("1"));
        assertThat(map.get("oracle.cz"), is("2"));
    }

    @Test
    public void testGetSupportedMediaTypes() {
        HoconConfigParser parser = new HoconConfigParser();

        assertThat(parser.getSupportedMediaTypes(), is(not(empty())));
    }

    @Test
    public void testCustomTypeMapping() {
        Config config = Config
                .withSources(ConfigSources.from(AppType.DEF, HoconConfigParser.MEDIA_TYPE_APPLICATION_JSON))
                .addParser(new HoconConfigParser())
                .addMapper(AppType.class, new AppTypeMapper())
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .disableParserServices()
                .disableMapperServices()
                .disableFilterServices()
                .build();
        AppType app = config.get("app").as(AppType.class);
        assertThat("greeting", app.getGreeting(), is(AppType.GREETING));
        assertThat("name", app.getName(), is(AppType.NAME));
        assertThat("page-size", app.getPageSize(), is(AppType.PAGE_SIZE));
        assertThat("basic-range", app.getBasicRange(), is(AppType.BASIC_RANGE));

    }

    //
    // helper
    //

    @FunctionalInterface
    private interface StringContent extends Content {
        @Override
        default String getMediaType() {
            return HoconConfigParser.MEDIA_TYPE_APPLICATION_HOCON;
        }

        @Override
        default Reader asReadable() {
            return new StringReader(getContent());
        }

        String getContent();
    }

    public static class AppType {

        private static final String GREETING = "Hello";
        private static final String NAME = "Demo";
        private static final int PAGE_SIZE = 20;
        private static final List<Integer> BASIC_RANGE = CollectionsHelper.listOf(-20, 20);

        static final String DEF = ""
                + "app {\n"
                + "  greeting = \"" + GREETING + "\"\n"
                + "  name = \"" + NAME + "\"\n"
                + "  page-size = " + PAGE_SIZE + "\n"
                + "  basic-range = [ " + BASIC_RANGE.get(0) + ", " + BASIC_RANGE.get(1) + " ]\n"
                + "  storagePassphrase = \"${AES=thisIsEncriptedPassphrase}\""
                + "}";

        private String greeting;
        private String name;
        private int pageSize;
        private List<Integer> basicRange;
        private String storagePassphrase;

        public AppType(
                String name,
                String greeting,
                int pageSize,
                List<Integer> basicRange,
                String storagePassphrase) {
            this.name = name;
            this.greeting = greeting;
            this.pageSize = pageSize;
            this.basicRange = copyBasicRange(basicRange);
            this.storagePassphrase = storagePassphrase;
        }

        private List<Integer> copyBasicRange(List<Integer> source) {
            return (source != null) ? new ArrayList<>(source) : Collections.emptyList();
        }

        public String getGreeting() {
            return greeting;
        }

        public String getName() {
            return name;
        }

        public int getPageSize() {
            return pageSize;
        }

        public List<Integer> getBasicRange() {
            return basicRange;
        }

        public String getStoragePassphrase() {
            return storagePassphrase;
        }
    }

    private static class AppTypeMapper implements ConfigMapper<AppType> {

        @Override
        public AppType apply(Config config) throws ConfigMappingException, MissingValueException {
            AppType app = new AppType(
                config.get("name").asString(),
                config.get("greeting").asString(),
                config.get("page-size").asInt(),
                config.get("basic-range").asList(Integer.class),
                config.get("storagePassphrase").asString()
            );

            return app;
        }
    }
}