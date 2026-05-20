'use strict';

/**
 * tv-auth.routes.js
 *
 * Rutas Express para el flujo de activación TV (Device Authorization Grant).
 *
 * Montar en el backend con:
 *   const tvAuth = require('./routes/tv-auth.routes');
 *   app.use('/api/tv', tvAuth);
 *
 * Endpoints resultantes:
 *   POST /api/tv/device-code   ← TV llama al iniciar pantalla de login
 *   GET  /api/tv/poll          ← TV llama cada 5s esperando confirmación
 *   POST /api/tv/activate      ← Página web llama al confirmar credenciales
 *
 * Dependencias npm a instalar en el backend:
 *   npm install qrcode
 */

const express   = require('express');
const QRCode    = require('qrcode');
const deviceSvc = require('../services/device-code.service');

const router = express.Router();

const API_BASE_URL = process.env.API_BASE_URL || 'http://98.80.97.51';
const WEB_BASE_URL = process.env.WEB_BASE_URL || 'https://play.luki.tv';

// ── POST /api/tv/device-code ───────────────────────────────────────────────────
// El TV llama a este endpoint al mostrar la pantalla de activación.
// Devuelve el código visible, la URL de verificación y el QR en base64.

router.post('/device-code', async (req, res) => {
  try {
    const { deviceCode, userCode, expiresAt } = deviceSvc.createDeviceCode();

    const verificationUrl = `${WEB_BASE_URL}/activar?code=${userCode}`;

    const qrDataUrl = await QRCode.toDataURL(verificationUrl, {
      width:  240,
      margin: 1,
      color:  { dark: '#001E41', light: '#FFFFFF' },
      errorCorrectionLevel: 'M',
    });

    res.json({
      device_code:      deviceCode,
      user_code:        userCode,
      verification_url: verificationUrl,
      expires_in:       Math.floor((expiresAt - Date.now()) / 1000),
      interval:         deviceSvc.POLL_INTERVAL_S,
      qr_data_url:      qrDataUrl,   // data:image/png;base64,...
    });
  } catch (err) {
    console.error('[tv-auth] device-code error:', err);
    res.status(500).json({ error: 'Error al generar el código de activación' });
  }
});

// ── GET /api/tv/poll?device_code=xxx ──────────────────────────────────────────
// El TV hace polling cada POLL_INTERVAL_S segundos.
// Respuestas posibles:
//   { status: 'pending',    expires_in: N }
//   { status: 'authorized', access_token, user_id }
//   { status: 'expired' }

router.get('/poll', (req, res) => {
  const { device_code } = req.query;

  if (!device_code) {
    return res.status(400).json({ error: 'device_code es requerido' });
  }

  const entry = deviceSvc.getByDeviceCode(device_code);

  if (!entry || entry.expiresAt < Date.now()) {
    return res.json({ status: 'expired' });
  }

  if (entry.status === 'authorized') {
    return res.json({
      status:       'authorized',
      access_token: entry.accessToken,
      user_id:      entry.userId,
    });
  }

  res.json({
    status:     'pending',
    expires_in: Math.floor((entry.expiresAt - Date.now()) / 1000),
  });
});

// ── POST /api/tv/activate ──────────────────────────────────────────────────────
// La página web /activar llama a este endpoint tras validar el código y
// autenticar al usuario con su cédula y clave.
//
// Body: { user_code, cedula, clave, device_id? }
// Response: { success: true } | { error: string }

router.post('/activate', async (req, res) => {
  const { user_code, cedula, clave, device_id } = req.body ?? {};

  // ── Validación de entrada ────────────────────────────────────────────────
  if (!user_code || !cedula || !clave) {
    return res.status(400).json({
      error: 'Los campos user_code, cedula y clave son requeridos',
    });
  }

  // ── Buscar el device_code por user_code ──────────────────────────────────
  const entry = deviceSvc.getByUserCode(user_code);

  if (!entry) {
    return res.status(404).json({
      error: 'Código inválido. Verifica que lo hayas escrito correctamente.',
    });
  }

  if (entry.expiresAt < Date.now()) {
    return res.status(410).json({
      error: 'El código ha expirado. Genera uno nuevo desde tu TV.',
    });
  }

  if (entry.status !== 'pending') {
    return res.status(409).json({
      error: 'Este código ya fue utilizado.',
    });
  }

  // ── Autenticar contra el backend existente ───────────────────────────────
  let authData;
  try {
    const authRes = await fetch(`${API_BASE_URL}/auth/app/id-login`, {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      body:    JSON.stringify({
        idNumber: cedula.trim(),
        password: clave,
        deviceId: device_id || `tv-activate-${Date.now()}`,
      }),
    });

    if (!authRes.ok) {
      const body = await authRes.json().catch(() => ({}));
      return res.status(401).json({
        error: body.message || 'Cédula o clave incorrectos',
      });
    }

    authData = await authRes.json();
  } catch (err) {
    console.error('[tv-auth] activate — error llamando a id-login:', err);
    return res.status(502).json({
      error: 'No se pudo conectar con el servidor. Intenta de nuevo.',
    });
  }

  // ── Extraer token (acepta distintas shapes del backend) ──────────────────
  const accessToken =
    authData.accessToken  ||
    authData.access_token ||
    authData.token        ||
    null;

  const userId =
    authData.userId      ||
    authData.user?.id    ||
    authData.id          ||
    null;

  if (!accessToken) {
    console.error('[tv-auth] activate — backend no devolvió token:', authData);
    return res.status(502).json({ error: 'Respuesta inesperada del servidor' });
  }

  // ── Autorizar el device_code ─────────────────────────────────────────────
  const ok = deviceSvc.authorize(user_code, accessToken, userId);
  if (!ok) {
    return res.status(409).json({ error: 'No se pudo autorizar el código' });
  }

  res.json({ success: true, message: '¡TV vinculada correctamente!' });
});

module.exports = router;
