#include <jni.h>
#include <string>
#include <sstream>
#include <iomanip>
#include <cstdint>

uint32_t fnv1a_hash(const std::string& str) {
    uint32_t hash = 2166136261u;
    for (unsigned char c : str) {
        hash ^= c;
        hash *= 16777619u;
    }
    return hash;
}

std::string to_hex(const std::string& input) {
    std::ostringstream oss;
    for (unsigned char c : input) {
        oss << std::hex << std::setw(2) << std::setfill('0') << (int)c;
    }
    return oss.str();
}

std::string from_hex(const std::string& hex) {
    std::string output;
    if (hex.length() % 2 != 0) return "";
    for (size_t i = 0; i < hex.length(); i += 2) {
        std::string byteString = hex.substr(i, 2);
        char byte = (char)strtol(byteString.c_str(), nullptr, 16);
        output.push_back(byte);
    }
    return output;
}

std::string cipher_transform(const std::string& data, const std::string& key) {
    std::string k = key.empty() ? "secure_key" : key;
    std::string out = data;
    uint32_t seed = fnv1a_hash(k);

    for (size_t i = 0; i < data.size(); ++i) {
        unsigned char key_byte = k[i % k.size()];
        unsigned char stream_byte = (unsigned char)((seed >> ((i % 4) * 8)) & 0xFF);
        out[i] = data[i] ^ key_byte ^ stream_byte ^ (unsigned char)(i * 31);
    }
    return out;
}

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_mychat_app_MainActivity_registerEmailNative(JNIEnv* env, jobject /* this */,
                                                     jstring email, jstring password) {
    if (!email || !password) return env->NewStringUTF("ERROR:Заполните все поля");
    
    const char* cEmail = env->GetStringUTFChars(email, nullptr);
    const char* cPass = env->GetStringUTFChars(password, nullptr);

    std::string sEmail(cEmail ? cEmail : "");
    std::string sPass(cPass ? cPass : "");

    env->ReleaseStringUTFChars(email, cEmail);
    env->ReleaseStringUTFChars(password, cPass);

    if (sEmail.find('@') == std::string::npos || sEmail.length() < 5) {
        return env->NewStringUTF("ERROR:Введите корректный Email");
    }
    if (sPass.length() < 4) {
        return env->NewStringUTF("ERROR:Пароль должен быть от 4 символов");
    }

    uint32_t h1 = fnv1a_hash("auth_" + sEmail);
    uint32_t h2 = fnv1a_hash("salt_" + sPass);

    std::ostringstream oss;
    oss << "ID-" << std::uppercase << std::hex << std::setw(8) << std::setfill('0') << h1
        << "-" << std::setw(4) << (h2 & 0xFFFF);
    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_mychat_app_MainActivity_getRoomChannel(JNIEnv* env, jobject /* this */, jstring roomKey) {
    if (!roomKey) return env->NewStringUTF("chat_global");
    const char* rawKey = env->GetStringUTFChars(roomKey, nullptr);
    std::string key(rawKey ? rawKey : "global");
    env->ReleaseStringUTFChars(roomKey, rawKey);

    uint32_t h = fnv1a_hash("room_chan_" + key);
    std::ostringstream oss;
    oss << "chat_" << std::hex << h;
    return env->NewStringUTF(oss.str().c_str());
}

JNIEXPORT jstring JNICALL
Java_com_mychat_app_MainActivity_encryptPacket(JNIEnv* env, jobject /* this */,
                                               jstring author, jstring message, jstring secretKey) {
    const char* cAuthor = author ? env->GetStringUTFChars(author, nullptr) : "User";
    const char* cMsg = message ? env->GetStringUTFChars(message, nullptr) : "";
    const char* cKey = secretKey ? env->GetStringUTFChars(secretKey, nullptr) : "key";

    std::string sAuthor(cAuthor);
    std::string sMsg(cMsg);
    std::string sKey(cKey);

    if (author) env->ReleaseStringUTFChars(author, cAuthor);
    if (message) env->ReleaseStringUTFChars(message, cMsg);
    if (secretKey) env->ReleaseStringUTFChars(secretKey, cKey);

    std::string payload = sAuthor + "::" + sMsg;
    uint32_t checksum = fnv1a_hash(payload);

    std::ostringstream packet;
    packet << std::hex << std::setw(8) << std::setfill('0') << checksum << "|" << payload;

    std::string encrypted = cipher_transform(packet.str(), sKey);
    return env->NewStringUTF(to_hex(encrypted).c_str());
}

JNIEXPORT jstring JNICALL
Java_com_mychat_app_MainActivity_decryptPacket(JNIEnv* env, jobject /* this */,
                                               jstring hexPacket, jstring secretKey) {
    if (!hexPacket) return env->NewStringUTF("");
    const char* cHex = env->GetStringUTFChars(hexPacket, nullptr);
    const char* cKey = secretKey ? env->GetStringUTFChars(secretKey, nullptr) : "key";

    std::string sHex(cHex ? cHex : "");
    std::string sKey(cKey ? cKey : "key");

    env->ReleaseStringUTFChars(hexPacket, cHex);
    if (secretKey) env->ReleaseStringUTFChars(secretKey, cKey);

    std::string rawEncrypted = from_hex(sHex);
    if (rawEncrypted.size() < 10) return env->NewStringUTF("");

    std::string decrypted = cipher_transform(rawEncrypted, sKey);
    if (decrypted.length() <= 9 || decrypted[8] != '|') {
        return env->NewStringUTF("Система::⚠️ Ошибка расшифровки сообщения");
    }

    std::string expectedHashHex = decrypted.substr(0, 8);
    std::string payload = decrypted.substr(9);

    std::ostringstream actualHash;
    actualHash << std::hex << std::setw(8) << std::setfill('0') << fnv1a_hash(payload);

    if (expectedHashHex != actualHash.str()) {
        return env->NewStringUTF("Система::⚠️ Неверный ключ комнаты");
    }

    return env->NewStringUTF(payload.c_str());
}

}
