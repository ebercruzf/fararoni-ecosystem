/*
 * Copyright 2026 Eber Cruz Fararoni
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
 *
 * ---------------------------------------------------------------------------
 *
 * Copyright 2026 Eber Cruz Fararoni
 *
 * Licenciado bajo la Licencia Apache, Version 2.0 (la "Licencia");
 * no puede usar este archivo excepto en cumplimiento con la Licencia.
 * Puede obtener una copia de la Licencia en
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * A menos que lo exija la ley aplicable o se acuerde por escrito, el software
 * distribuido bajo la Licencia se distribuye "TAL CUAL", SIN GARANTIAS NI
 * CONDICIONES DE NINGUN TIPO, ya sean expresas o implicitas.
 * Consulte la Licencia para conocer el lenguaje especifico que rige los
 * permisos y las limitaciones de la misma.
 */
package dev.fararoni.test;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileReader;

/**
 * @author Eber Cruz
 * @version 1.0.0
 */
public class BrokenClassForTesting {

    // ERROR 1: Variable no usada (warning)
    private String unusedVariable = "nunca se usa";

    // ERROR 2: Nombre de constante incorrecto (debería ser UPPER_CASE)
    public static final String miConstante = "valor";

    // ERROR 3: Campo público mutable (violación de encapsulamiento)
    public List<String> publicList = new ArrayList<>();

    // ERROR 4: Null pointer potencial
    private String nullableString = null;

    public static void main(String[] args) {
        BrokenClassForTesting test = new BrokenClassForTesting();

        System.out.println("=== Prueba de Fararoni Stacktrace Analyzer ===\n");

        // Descomentar cada línea para probar diferentes excepciones:

        // TEST 1: NullPointerException
        test.causeNullPointer();

        // TEST 2: ArrayIndexOutOfBoundsException
        // test.causeArrayOutOfBounds();

        // TEST 3: ArithmeticException (división por cero)
        // test.causeDivisionByZero();

        // TEST 4: FileNotFoundException
        // test.causeFileNotFound();

        // TEST 5: ClassCastException
        // test.causeClassCast();
    }

    public void causeNullPointer() {
        String s = null;
        System.out.println(s.length()); // NullPointerException
    }

    public void causeArrayOutOfBounds() {
        int[] array = new int[5];
        System.out.println(array[10]); // ArrayIndexOutOfBoundsException
    }

    public void causeDivisionByZero() {
        int a = 10;
        int b = 0;
        System.out.println(a / b); // ArithmeticException
    }

    public void causeFileNotFound() {
        try {
            FileReader fr = new FileReader("/archivo/que/no/existe.txt");
        } catch (Exception e) {
            throw new RuntimeException("Error al leer archivo: " + e.getMessage(), e);
        }
    }

    public void causeClassCast() {
        Object obj = "Soy un String";
        Integer num = (Integer) obj; // ClassCastException
    }

    // ERROR 5: Método muy largo (God Method)
    public void metodoDemasiadoLargo(String input) {
        System.out.println("Línea 1");
        System.out.println("Línea 2");
        System.out.println("Línea 3");
        System.out.println("Línea 4");
        System.out.println("Línea 5");
        // Imagina 100 líneas más aquí...
        if (input != null) {
            if (input.length() > 0) {
                if (input.startsWith("A")) {
                    if (input.endsWith("Z")) {
                        // Demasiado anidamiento
                        System.out.println("Encontrado: " + input);
                    }
                }
            }
        }
    }

    // ERROR 6: Código duplicado
    public int calcularAreaRectangulo(int ancho, int alto) {
        int resultado = ancho * alto;
        System.out.println("El área es: " + resultado);
        return resultado;
    }

    public int calcularAreaCuadrado(int lado) {
        int resultado = lado * lado; // Duplicado de la lógica anterior
        System.out.println("El área es: " + resultado);
        return resultado;
    }

    // ERROR 7: Catch genérico (anti-patrón)
    public void manejoDeErroresMalo() {
        try {
            File f = new File("/tmp/test.txt");
            // operaciones...
        } catch (Exception e) {
            // Catch genérico - mala práctica
            e.printStackTrace(); // Nunca usar printStackTrace en producción
        }
    }

    // ERROR 8: Números mágicos
    public double calcularDescuento(double precio) {
        if (precio > 100) {
            return precio * 0.15; // ¿Qué es 0.15? Número mágico
        } else if (precio > 50) {
            return precio * 0.10; // Otro número mágico
        }
        return precio * 0.05; // Y otro más
    }

    // ERROR 9: Método que hace demasiadas cosas (violación SRP)
    public void procesarUsuario(String nombre, String email, int edad) {
        // Validar
        if (nombre == null || nombre.isEmpty()) {
            throw new IllegalArgumentException("Nombre inválido");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email inválido");
        }

        // Guardar en base de datos (simulado)
        System.out.println("Guardando en DB: " + nombre);

        // Enviar email de bienvenida
        System.out.println("Enviando email a: " + email);

        // Actualizar estadísticas
        System.out.println("Actualizando stats para edad: " + edad);

        // Loggear
        System.out.println("Log: Usuario procesado");
    }

    // ERROR 10: Retorno de null en lugar de Optional o colección vacía
    public String buscarUsuario(int id) {
        if (id == 1) {
            return "Usuario Encontrado";
        }
        return null; // Debería retornar Optional.empty()
    }
    //Hola de prueba

    // ERROR 11: Boolean como parámetro (Flag Argument anti-pattern)
    public void procesarDatos(List<String> datos, boolean ordenar, boolean filtrar, boolean mayusculas) {
        if (ordenar) {
            // ordenar...
        }
        if (filtrar) {
            // filtrar...
        }
        if (mayusculas) {
            // convertir...
        }
    }

    // ERROR 12: Getter que modifica estado (efecto secundario)
    private int contador = 0;

    public int getContador() {
        contador++; // MALO: getter no debería modificar estado
        return contador;
    }

    // TODO: Implementar este método
    public void metodoPorImplementar() {
        // Fararoni debería sugerir una implementación
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
