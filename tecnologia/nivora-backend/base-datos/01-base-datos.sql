-- Base de datos propia del backend de Nivora.
-- Ejecutar con una cuenta administradora de MySQL.
CREATE DATABASE IF NOT EXISTS nivora
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE nivora;

SELECT DATABASE() AS base_actual,
       VERSION() AS version_mysql;