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

    /// Llamar una vez con la ventana ya visible (hilo de JavaFX).
    /// Quita cuentas que no sean offline y, si no queda ninguna, pide el nombre al jugador.
    public static void ensureAccount() {
        try {
            Accounts.getAccounts().removeIf(account -> !(account instanceof OfflineAccount));
            if (Accounts.getSelectedAccount() == null && !Accounts.getAccounts().isEmpty()) {
                Accounts.setSelectedAccount(Accounts.getAccounts().get(0));
            }
            if (Accounts.getSelectedAccount() == null) {
                askNickname();
            }
        } catch (Throwable e) {
            LOG.warning("No se pudo comprobar la cuenta del jugador", e);
        }
    }

    /// Pide el nombre de jugador y lo guarda como única cuenta offline.
    public static void askNickname() {
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
            } catch (Throwable e) {
                LOG.warning("No se pudo crear la cuenta offline", e);
                handler.reject("No se pudo guardar el nombre: " + e.getMessage());
            }
        });
    }
}
