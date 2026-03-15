/*
 * Copyright (C) 2026 Eber Cruz Fararoni. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fararoni.core.core.dispatcher;

import dev.fararoni.bus.agent.api.ParameterValidationException;
import dev.fararoni.bus.agent.api.ToolParameter;
import dev.fararoni.bus.agent.api.ToolSkill;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Eber Cruz
 * @since 1.0.0
 */
public class ReflectionToolInvoker {

    private final ObjectMapper objectMapper;
    private final Map<String, MethodHandle> handleCache;

    public ReflectionToolInvoker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.handleCache = new ConcurrentHashMap<>();
    }

    public ReflectionToolInvoker() {
        this(new ObjectMapper());
    }

    public Object invoke(ToolSkill skill, Method method, Map<String, Object> params) throws Exception {
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            String paramName = extractParamName(param);
            Object value = params.get(paramName);

            if (value == null && isRequired(param)) {
                throw new ParameterValidationException(paramName);
            }

            if (value == null) {
                value = getDefaultValue(param, param.getType());
            }

            if (value != null) {
                args[i] = convertValue(value, param.getType());
            } else {
                args[i] = null;
            }
        }

        String cacheKey = skill.getClass().getName() + "#" + method.getName();
        MethodHandle handle = handleCache.get(cacheKey);

        if (handle != null) {
            try {
                return handle.bindTo(skill).invokeWithArguments(args);
            } catch (Throwable t) {
                if (t instanceof Exception ex) {
                    throw ex;
                }
                throw new RuntimeException(t);
            }
        }

        return method.invoke(skill, args);
    }

    public void cacheMethod(ToolSkill skill, Method method) {
        String cacheKey = skill.getClass().getName() + "#" + method.getName();

        if (!handleCache.containsKey(cacheKey)) {
            try {
                method.setAccessible(true);
                MethodHandle handle = MethodHandles.lookup().unreflect(method);
                handleCache.put(cacheKey, handle);
            } catch (IllegalAccessException e) {
            }
        }
    }

    public void clearCache() {
        handleCache.clear();
    }

    private String extractParamName(Parameter param) {
        if (param.isAnnotationPresent(ToolParameter.class)) {
            return param.getAnnotation(ToolParameter.class).name();
        }
        return param.getName();
    }

    private boolean isRequired(Parameter param) {
        if (param.isAnnotationPresent(ToolParameter.class)) {
            return param.getAnnotation(ToolParameter.class).required();
        }
        return param.getType().isPrimitive();
    }

    private Object getDefaultValue(Parameter param, Class<?> type) {
        if (param.isAnnotationPresent(ToolParameter.class)) {
            String defaultStr = param.getAnnotation(ToolParameter.class).defaultValue();
            if (!defaultStr.isEmpty()) {
                return convertValue(defaultStr, type);
            }
        }

        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0f;
        if (type == double.class) return 0.0;
        if (type == char.class) return '\0';

        return null;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (targetType == String.class) {
            return value.toString();
        }

        if (value instanceof String strValue) {
            return convertFromString(strValue, targetType);
        }

        if (value instanceof Number numValue) {
            return convertFromNumber(numValue, targetType);
        }

        try {
            return objectMapper.convertValue(value, targetType);
        } catch (IllegalArgumentException e) {
            throw new ParameterValidationException(
                "value",
                targetType.getSimpleName(),
                value
            );
        }
    }

    private Object convertFromString(String value, Class<?> targetType) {
        try {
            if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            }
            if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            }
            if (targetType == double.class || targetType == Double.class) {
                return Double.parseDouble(value);
            }
            if (targetType == float.class || targetType == Float.class) {
                return Float.parseFloat(value);
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value);
            }
            if (targetType == byte.class || targetType == Byte.class) {
                return Byte.parseByte(value);
            }
            if (targetType == short.class || targetType == Short.class) {
                return Short.parseShort(value);
            }
        } catch (NumberFormatException e) {
            throw new ParameterValidationException("value", targetType.getSimpleName(), value);
        }

        return objectMapper.convertValue(value, targetType);
    }

    private Object convertFromNumber(Number value, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return value.intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return value.longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return value.doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return value.floatValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return value.byteValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return value.shortValue();
        }
        if (targetType == String.class) {
            return value.toString();
        }

        return value;
    }
}
