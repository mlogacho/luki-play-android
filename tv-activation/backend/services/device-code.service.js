'use strict';

/**
 * device-code.service.js
 *
 * Gestiona el ciclo de vida de los códigos de activación TV.
 * Implementa el patrón OAuth 2.0 Device Authorization Grant (RFC 8628).
 *
 * Almacenamiento: Map en memoria con TTL.
 * Para producción: reemplaza deviceCodes/userCodes por Redis con EXPIRE.
 *
 * Ejemplo Redis (drop-in replacement):
 *   await redis.set(`dc:${deviceCode}`, JSON.stringify(data), 'EX', TTL_SECONDS);
 *   await redis.set(`uc:${userCode}`, deviceCode, 'EX', TTL_SECONDS);
 */

const crypto = require('crypto');

// ── Configuración ──────────────────────────────────────────────────────────────

const CODE_TTL_MS      = 5 * 60 * 1000; // 5 minutos
const POLL_INTERVAL_S  = 5;             // TV debe esperar 5s entre polls
const CLEANUP_EVERY_MS = 60 * 1000;    // Limpieza de expirados cada 1 min

// Caracteres sin ambigüedad: sin 0/O ni 1/I/L
const SAFE_CHARS = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';

// ── Almacenamiento ─────────────────────────────────────────────────────────────

/** @type {Map<string, DeviceCodeEntry>} device_code → entrada */
const deviceCodes = new Map();

/** @type {Map<string, string>} user_code → device_code (índice inverso) */
const userCodes = new Map();

/**
 * @typedef {Object} DeviceCodeEntry
 * @property {string} userCode
 * @property {'pending'|'authorized'|'expired'} status
 * @property {string|null} accessToken
 * @property {string|null} userId
 * @property {number} expiresAt  timestamp ms
 * @property {number} createdAt  timestamp ms
 */

// ── Limpieza automática ────────────────────────────────────────────────────────

const _cleanup = setInterval(() => {
  const now = Date.now();
  for (const [dc, entry] of deviceCodes) {
    if (entry.expiresAt < now) {
      userCodes.delete(entry.userCode);
      deviceCodes.delete(dc);
    }
  }
}, CLEANUP_EVERY_MS);

// Evita que el intervalo bloquee el proceso al cerrar
if (_cleanup.unref) _cleanup.unref();

// ── Helpers ────────────────────────────────────────────────────────────────────

function _randomChars(n) {
  return Array.from(
    { length: n },
    () => SAFE_CHARS[Math.floor(Math.random() * SAFE_CHARS.length)]
  ).join('');
}

/** Genera un user_code único con formato XXX-XXXX */
function _generateUserCode() {
  let code;
  let attempts = 0;
  do {
    code = `${_randomChars(3)}-${_randomChars(4)}`;
    if (++attempts > 100) throw new Error('No se pudo generar un código único');
  } while (userCodes.has(code));
  return code;
}

/** Normaliza un user_code ingresado por el usuario (ej: "abc 1234" → "ABC-1234") */
function normalizeUserCode(raw) {
  const cleaned = raw.toUpperCase().replace(/[\s\-_]/g, '');
  if (cleaned.length !== 7) return null;
  return `${cleaned.slice(0, 3)}-${cleaned.slice(3)}`;
}

// ── API pública ────────────────────────────────────────────────────────────────

/**
 * Crea un nuevo par device_code / user_code.
 * @returns {{ deviceCode: string, userCode: string, expiresAt: number }}
 */
function createDeviceCode() {
  const deviceCode = crypto.randomUUID();
  const userCode   = _generateUserCode();
  const now        = Date.now();

  /** @type {DeviceCodeEntry} */
  const entry = {
    userCode,
    status:      'pending',
    accessToken: null,
    userId:      null,
    expiresAt:   now + CODE_TTL_MS,
    createdAt:   now,
  };

  deviceCodes.set(deviceCode, entry);
  userCodes.set(userCode, deviceCode);

  return { deviceCode, userCode, expiresAt: entry.expiresAt };
}

/**
 * Consulta el estado de un device_code (usado por el TV al hacer polling).
 * @param {string} deviceCode
 * @returns {DeviceCodeEntry|null}
 */
function getByDeviceCode(deviceCode) {
  return deviceCodes.get(deviceCode) ?? null;
}

/**
 * Busca una entrada por el user_code visible (ingresado desde el teléfono).
 * @param {string} rawUserCode  Acepta "abc1234", "ABC 1234", "ABC-1234"
 * @returns {{ deviceCode: string } & DeviceCodeEntry | null}
 */
function getByUserCode(rawUserCode) {
  const normalized = normalizeUserCode(rawUserCode);
  if (!normalized) return null;

  const deviceCode = userCodes.get(normalized);
  if (!deviceCode) return null;

  const entry = deviceCodes.get(deviceCode);
  if (!entry) return null;

  return { deviceCode, ...entry };
}

/**
 * Marca un device_code como autorizado tras login exitoso.
 * @param {string} rawUserCode
 * @param {string} accessToken  JWT del backend
 * @param {string} userId
 * @returns {boolean} true si se autorizó, false si no existe/expiró/ya usado
 */
function authorize(rawUserCode, accessToken, userId) {
  const normalized = normalizeUserCode(rawUserCode);
  if (!normalized) return false;

  const deviceCode = userCodes.get(normalized);
  if (!deviceCode) return false;

  const entry = deviceCodes.get(deviceCode);
  if (!entry) return false;
  if (entry.expiresAt < Date.now()) return false;
  if (entry.status !== 'pending') return false;

  entry.status      = 'authorized';
  entry.accessToken = accessToken;
  entry.userId      = userId;

  return true;
}

// ── Exports ────────────────────────────────────────────────────────────────────

module.exports = {
  createDeviceCode,
  getByDeviceCode,
  getByUserCode,
  authorize,
  normalizeUserCode,
  CODE_TTL_MS,
  POLL_INTERVAL_S,
};
