-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Servidor: 127.0.0.1
-- Tiempo de generación: 19-09-2026 a las 20:03:08
-- Versión del servidor: 10.4.32-MariaDB
-- Versión de PHP: 8.0.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Base de datos: `andesstay`
--

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `audit_events`
--

CREATE TABLE `audit_events` (
  `id` bigint(20) NOT NULL,
  `action` varchar(255) NOT NULL,
  `description` varchar(255) NOT NULL,
  `event_date` datetime(6) NOT NULL,
  `reservation_id` bigint(20) NOT NULL,
  `user` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `audit_events`
--

INSERT INTO `audit_events` (`id`, `action`, `description`, `event_date`, `reservation_id`, `user`) VALUES
(1, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Prueba Auditoria', '2026-09-18 19:13:23.000000', 17, ''),
(2, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-18 19:20:13.000000', 17, ''),
(3, 'CHECKIN_PENDIENTE', 'La reserva cambió de CONFIRMADA a CHECKIN_PENDIENTE', '2026-09-18 19:26:00.000000', 17, ''),
(4, 'CHECKIN', 'La reserva pasó de CHECKIN_PENDIENTE a EN_ESTADÍA', '2026-09-18 19:28:00.000000', 17, ''),
(5, 'CHECKOUT', 'La reserva pasó de EN_ESTADÍA a CHECKOUT', '2026-09-18 19:28:25.000000', 17, ''),
(6, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Prueba Auditoria Kafka', '2026-09-18 19:37:14.000000', 18, ''),
(7, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Prueba React', '2026-09-18 21:43:47.000000', 19, ''),
(8, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-18 21:45:34.000000', 19, ''),
(9, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Prueba React 2', '2026-09-18 22:19:48.000000', 20, ''),
(10, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-18 22:20:24.000000', 20, ''),
(11, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-18 22:31:07.000000', 18, ''),
(12, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Pepe', '2026-09-18 23:09:28.000000', 21, 'cris.vergarac@duocuc.cl'),
(13, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Prueba BFF', '2026-09-19 00:48:28.000000', 22, 'cris.vergarac@duocuc.cl'),
(14, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-19 11:55:08.000000', 22, 'cris.vergarac@duocuc.cl'),
(15, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-19 11:57:19.000000', 13, 'cris.vergarac@duocuc.cl'),
(16, 'RESERVA_CREADA', 'Se creó la reserva para el huésped juan jose', '2026-09-19 12:18:45.000000', 23, 'cris.vergarac@duocuc.cl'),
(17, 'RESERVA_CANCELADA', 'La reserva cambió de CREADA a CANCELADA', '2026-09-19 12:18:59.000000', 23, 'cris.vergarac@duocuc.cl'),
(18, 'CHECKIN_PENDIENTE', 'La reserva cambió de CONFIRMADA a CHECKIN_PENDIENTE', '2026-09-19 12:19:02.000000', 22, 'cris.vergarac@duocuc.cl'),
(19, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-19 12:19:13.000000', 21, 'cris.vergarac@duocuc.cl'),
(20, 'CHECKIN_PENDIENTE', 'La reserva cambió de CONFIRMADA a CHECKIN_PENDIENTE', '2026-09-19 12:19:17.000000', 21, 'cris.vergarac@duocuc.cl'),
(21, 'CHECKIN', 'La reserva pasó de CHECKIN_PENDIENTE a EN_ESTADÍA', '2026-09-19 12:19:20.000000', 21, 'cris.vergarac@duocuc.cl'),
(22, 'CHECKOUT', 'La reserva pasó de EN_ESTADÍA a CHECKOUT', '2026-09-19 12:19:21.000000', 21, 'cris.vergarac@duocuc.cl'),
(23, 'RESERVA_CREADA', 'Se creó la reserva para el huésped zzz', '2026-09-19 12:21:16.000000', 24, 'cris.vergarac@duocuc.cl'),
(24, 'RESERVA_CREADA', 'Se creó la reserva para el huésped Lol', '2026-09-19 12:30:43.000000', 25, 'cris.vergarac@duocuc.cl'),
(25, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-19 12:30:46.000000', 25, 'cris.vergarac@duocuc.cl'),
(26, 'RESERVA_CREADA', 'Se creó la reserva para el huésped pepesito', '2026-09-19 12:55:57.000000', 26, 'cris.vergarac@duocuc.cl'),
(27, 'RESERVA_CONFIRMADA', 'La reserva cambió de CREADA a CONFIRMADA', '2026-09-19 12:58:30.000000', 26, 'cris.vergarac@duocuc.cl');

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `reservations`
--

CREATE TABLE `reservations` (
  `id` bigint(20) NOT NULL,
  `check_in` date NOT NULL,
  `check_out` date NOT NULL,
  `guest_name` varchar(255) NOT NULL,
  `status` varchar(255) NOT NULL,
  `unit_id` bigint(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `reservations`
--

INSERT INTO `reservations` (`id`, `check_in`, `check_out`, `guest_name`, `status`, `unit_id`) VALUES
(2, '2026-10-01', '2026-10-05', 'Juan Jose', 'CHECKOUT', 1),
(3, '2026-10-01', '2026-10-05', 'Gato Por Liebre', 'CANCELADA', 1),
(4, '2026-10-10', '2026-10-15', 'Cristobal', 'CONFIRMADA', 2),
(5, '2026-10-10', '2026-10-15', 'Jaime', 'CONFIRMADA', 2),
(6, '2026-10-10', '2026-10-15', 'Prime', 'CONFIRMADA', 2),
(7, '2026-10-12', '2026-10-18', 'Pedro', 'CANCELADA', 2),
(8, '2026-10-20', '2026-10-25', 'Pablo', 'CONFIRMADA', 2),
(9, '2026-10-26', '2026-10-30', 'Carlos', 'CONFIRMADA', 2),
(10, '2026-11-01', '2026-11-05', 'Test Flujo', 'CHECKOUT', 2),
(11, '2026-11-01', '2026-11-05', 'Prime jeje', 'CHECKOUT', 2),
(12, '2026-12-01', '2026-12-05', 'Validacion Test', 'CREADA', 2),
(13, '2027-01-10', '2027-01-15', 'Reserva Valida Final', 'CONFIRMADA', 2),
(14, '2026-12-01', '2026-12-05', 'Prueba RabbitMQ', 'CREADA', 2),
(15, '2027-01-10', '2027-01-15', 'Prueba Kafka', 'CREADA', 2),
(16, '2027-01-10', '2027-01-15', 'Prueba Kafka', 'CREADA', 2),
(17, '2027-02-01', '2027-02-05', 'Prueba Auditoria', 'CHECKOUT', 2),
(18, '2027-03-01', '2027-03-05', 'Prueba Auditoria Kafka', 'CONFIRMADA', 2),
(19, '2026-12-01', '2026-12-05', 'Prueba React', 'CONFIRMADA', 2),
(20, '2026-12-10', '2026-12-12', 'Prueba React 2', 'CONFIRMADA', 2),
(21, '2026-09-18', '2026-09-20', 'Pepe', 'CHECKOUT', 2),
(22, '2026-04-04', '2026-04-08', 'Prueba BFF', 'CHECKIN_PENDIENTE', 2),
(23, '2026-10-20', '2026-10-25', 'juan jose', 'CANCELADA', 2),
(24, '2026-10-20', '2026-10-22', 'zzz', 'CREADA', 2),
(25, '2026-05-01', '2026-05-05', 'Lol', 'CONFIRMADA', 2),
(26, '2026-01-01', '2026-01-10', 'pepesito', 'CONFIRMADA', 2);

-- --------------------------------------------------------

--
-- Estructura de tabla para la tabla `units`
--

CREATE TABLE `units` (
  `id` bigint(20) NOT NULL,
  `name` varchar(255) NOT NULL,
  `type` varchar(255) NOT NULL,
  `capacity` int(11) NOT NULL,
  `price` decimal(38,2) NOT NULL,
  `available` tinyint(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Volcado de datos para la tabla `units`
--

INSERT INTO `units` (`id`, `name`, `type`, `capacity`, `price`, `available`, `description`) VALUES
(2, 'Cabaña Los Andes', 'CABAÑA', 4, 65000.00, 0, 'Cabaña para cuatro personas');

--
-- Índices para tablas volcadas
--

--
-- Indices de la tabla `audit_events`
--
ALTER TABLE `audit_events`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `reservations`
--
ALTER TABLE `reservations`
  ADD PRIMARY KEY (`id`);

--
-- Indices de la tabla `units`
--
ALTER TABLE `units`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT de las tablas volcadas
--

--
-- AUTO_INCREMENT de la tabla `audit_events`
--
ALTER TABLE `audit_events`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=28;

--
-- AUTO_INCREMENT de la tabla `reservations`
--
ALTER TABLE `reservations`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=27;

--
-- AUTO_INCREMENT de la tabla `units`
--
ALTER TABLE `units`
  MODIFY `id` bigint(20) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
