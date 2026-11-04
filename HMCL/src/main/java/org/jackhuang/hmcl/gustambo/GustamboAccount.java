// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gustambo;

import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.auth.offline.OfflineAccountFactory;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;

import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Cuenta única y offline de GustamboClient: el jugador solo elige su nombre (nick).
///
/// No hay inicio de sesión Microsoft ni servidores authlib: la barra lateral solo muestra
/// el nombre y permite cambiarlo.
public final class GustamboAccount {
    private GustamboAccount() {
    }

    /// Nombres válidos de Minecraft: 3 a 16 caracteres, letras, números o guion bajo.
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    /// Prefijo del nombre temporal que se asigna al abrir por primera vez.
    private static final String DEFAULT_PREFIX = "Jugador_";

    /// Llamar una vez con la ventana ya visible (hilo de JavaFX).
    /// Quita cuentas que no sean offline y, si no queda ninguna, crea una cuenta temporal
    /// SIN abrir ningún cuadro: así nada bloquea la ventana al entrar. El nombre real se
    /// pide al pulsar JUGAR (o cuando el jugador toca su avatar).
    public static void ensureAccount() {
        try {
            Accounts.getAccounts().removeIf(account -> !(account instanceof OfflineAccount));
            if (Accounts.getSelectedAccount() == null && !Accounts.getAccounts().isEmpty()) {
                Accounts.setSelectedAccount(Accounts.getAccounts().get(0));
            }
            if (Accounts.getSelectedAccount() == null) {
                String name = DEFAULT_PREFIX + (1000 + new java.util.Random().nextInt(9000));
                OfflineAccount account = Accounts.FACTORY_OFFLINE.create(name, OfflineAccountFactory.getUUIDFromUserName(name));
                Accounts.getAccounts().setAll(java.util.List.of(account));
                Accounts.setSelectedAccount(account);
            }
        } catch (Throwable e) {
            LOG.warning("No se pudo comprobar la cuenta del jugador", e);
        }
    }

    /// `true` si el jugador todavía tiene el nombre temporal y no ha elegido el suyo.
    public static boolean hasDefaultName() {
        var account = Accounts.getSelectedAccount();
        return account == null || account.getProfileName().startsWith(DEFAULT_PREFIX);
    }

    /// Pide el nombre de jugador y lo guarda como única cuenta offline.
    public static void askNickname() {
        askNickname(null);
    }

    /// Igual que [#askNickname()], y ejecuta `onDone` cuando el nombre se guardó bien.
    public static void askNickname(Runnable onDone) {
        Controllers.prompt("Tu nombre de jugador (3-16 letras, números o _)", (value, handler) -> {
            String name = value == null ? "" : value.trim();
            if (!VALID_NAME.matcher(name).matches()) {
                handler.reject("Usa entre 3 y 16 letras, números o _ (sin espacios).");
                return;
            }
            try {
                OfflineAccount account = Accounts.FACTORY_OFFLINE.create(name, OfflineAccountFactory.getUUIDFromUserName(name));
                Accounts.getAccounts().setAll(java.util.List.of(account));
                Accounts.setSelectedAccount(account);
                handler.resolve();
                Controllers.showToast("Jugador: " + name);
                if (onDone != null) onDone.run();
            } catch (Throwable e) {
                LOG.warning("No se pudo crear la cuenta offline", e);
                handler.reject("No se pudo guardar el nombre: " + e.getMessage());
            }
        });
    }
}
