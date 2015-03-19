/*
 * Copyright (c) 2015 Tim Boudreau
 *
 * This file is part of Scamper.
 *
 * Scamper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.mastfrog.scamper.password.crypto;

import com.mastfrog.giulius.Dependencies;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import com.mastfrog.util.Exceptions;
import io.netty.util.CharsetUtil;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

/**
 *
 * @author Tim Boudreau
 */
public class Encrypter {

    ThreadLocal<Cipher> enCipher = new ThreadLocal<>();
    ThreadLocal<Cipher> deCipher = new ThreadLocal<>();
    private final int ROUNDS;
    public static final String SETTINGS_KEY_PASSPHRASE = "scamper.passphrase";
    public static final String DEFAULT_PASSPHRASE = "Change this before use!";
    public static final String SETTINGS_KEY_ROUNDS = "scamper.rounds";
    public static final int DEFAULT_ROUNDS = 1;
    private final byte[] key;

    @Inject
    public Encrypter(Settings settings) {
        this(settings.getString(SETTINGS_KEY_PASSPHRASE, DEFAULT_PASSPHRASE),
                settings.getInt(SETTINGS_KEY_ROUNDS, DEFAULT_ROUNDS));
        if (Dependencies.isProductionMode(settings)) {
            if (DEFAULT_PASSPHRASE.equals(settings.getString(SETTINGS_KEY_PASSPHRASE, DEFAULT_PASSPHRASE))) {
                throw new ConfigurationError("Will not run in production mode with default passphrase");
            }
        }
    }

    Encrypter(String password, int rounds) {
        this.ROUNDS = rounds;
        key = createKey(password);
    }

    private byte[] createKey(String password) {
        byte[] key = password.getBytes(CharsetUtil.UTF_8);
        int bits = (key.length * 8);
        if (bits > 448) {
            int amt = 448 / 8;
            byte[] nue = new byte[amt];
            System.arraycopy(key, 0, nue, 0, amt);
            int pos = amt;
            while (pos < key.length) {
                for (int i = 0; i < nue.length && pos < key.length; i++) {
                    nue[i] ^= key[pos++];
                }
            }
            key = nue;
        }
        return key;
    }

    private Cipher encipher() {
        try {
            SecretKeySpec KS = new SecretKeySpec(key, "Blowfish");
            Cipher enCipher = Cipher.getInstance("Blowfish");
            enCipher.init(Cipher.ENCRYPT_MODE, KS);
            this.enCipher.set(enCipher);
            return enCipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            return Exceptions.chuck(e);
        }
    }

    private Cipher decipher() {
        try {
            SecretKeySpec KS = new SecretKeySpec(key, "Blowfish");
//            Cipher deCipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
            Cipher deCipher = Cipher.getInstance("Blowfish");
            deCipher.init(Cipher.DECRYPT_MODE, KS);
            this.deCipher.set(deCipher);
            return deCipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
            return Exceptions.chuck(e);
        }
    }

    public byte[] encrypt(byte[] bytes) {
        if (bytes.length == 0) {
            return bytes;
        }
        try {
            byte[] base64 = Base64.getEncoder().encode(bytes);
            Cipher encipher = encipher();
            for (int i = 0; i < ROUNDS; i++) {
                bytes = encipher.doFinal(bytes);
            }
            return bytes;
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public String encrypt(String cleartext) {
        try {
            byte[] encrypted = cleartext.getBytes(CharsetUtil.UTF_8);
            Cipher encipher = encipher();
            for (int i = 0; i < ROUNDS; i++) {
                encrypted = encipher.doFinal(encrypted);
            }
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public byte[] decrypt(byte[] decrypted) {
        if (decrypted.length == 0) {
            return decrypted;
        }
        try {
            Cipher decipher = decipher();
            for (int i = 0; i < ROUNDS; i++) {
                decrypted = decipher.doFinal(decrypted);
            }
            return decrypted;
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            return Exceptions.chuck(ex);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] decrypted = Base64.getDecoder().decode(encrypted);
            Cipher decipher = decipher();
            for (int i = 0; i < ROUNDS; i++) {
                decrypted = decipher.doFinal(decrypted);
            }
            return new String(decrypted, CharsetUtil.UTF_8);
        } catch (IllegalBlockSizeException | BadPaddingException ex) {
            return Exceptions.chuck(ex);
        }
    }
}
