# Tienda Virtual · Aplicativo Android (entrega final)

Tienda virtual nativa para Android con tres roles: **Administrador**, **Vendedor** y **Comprador**. Esta versión conecta las interfaces de la entrega anterior con un backend real en Firebase. Incluye autenticación (también con huella), CRUDs completos, carrito y órdenes, geolocalización y pagos con ePayco.

| Componente | Implementación | Archivos principales |
|---|---|---|
| Autenticación | Firebase Authentication (correo y contraseña): registro, inicio y cierre de sesión, recuperación de contraseña. El rol se lee de Firestore. | `AuthRepository`, `LoginActivity`, `RegisterActivity`, `RecoverPasswordActivity` |
| Huella dactilar | `BiometricPrompt` + llave AES del Android Keystore que exige huella. Las credenciales se guardan cifradas. | `util/BiometricAuth`, `LoginActivity`, `ProfileActivity` |
| CRUD de usuarios | El Administrador crea (cuenta + perfil), lista, busca, edita, desactiva y elimina. | `UserRepository`, `AdminUsersActivity`, `AdminUserFormActivity` |
| CRUD de productos | Vendedor (sus productos) y Administrador (todos). La imagen se toma con la cámara o se elige de la galería, se comprime (JPEG ≤ 300 KB, 800 px) y se guarda en Firestore. | `ProductRepository`, `util/ImageCompressor`, `ProductFormActivity`, `SellerProductsActivity`, `AdminProductsActivity` |
| Carrito y órdenes | Carrito persistente en Firestore. Las órdenes guardan su historial con estados (pendiente → pagada → enviada → entregada) y se descuenta el stock en una transacción. | `CartRepository`, `OrderRepository`, `CartActivity`, `SellerOrdersActivity`, `BuyerOrdersActivity` |
| Geolocalización | Fused Location Provider (Google Play Services) + geocodificación inversa. Las coordenadas quedan en la orden y se abren en Google Maps. | `util/LocationHelper`, `CheckoutActivity`, `OrderMapLauncher` |
| Pasarela de pagos | Checkout estándar de ePayco en un WebView. La respuesta se confirma con la API de validación de ePayco. | `data/payment/EpaycoService`, `PaymentActivity` |

**Tecnologías:** Kotlin, XML con ViewBinding, corrutinas, Material Components 1.12, Firebase BoM 33.5.1 (Auth, Firestore), Play Services Location 21.3, AndroidX Biometric 1.1 y Glide 4.16.
**Versiones:** minSdk 24 · targetSdk 34 · compileSdk 34 · JDK 17 · AGP 8.5.2 · Gradle 8.7.

---

## 1. Puesta en marcha

### 1.1 Proyecto de Firebase
1. Crea un proyecto en <https://console.firebase.google.com>.
2. Agrega una app Android con el paquete **`com.tiendavirtual.app`** y descarga **`google-services.json`** en la carpeta **`app/`**. Ese archivo no se sube al repositorio.
3. **Authentication → Método de acceso:** habilita **Correo electrónico/contraseña**.
4. **Firestore Database:** créala en modo producción y pega en *Reglas* el contenido de `firebase/firestore.rules`.
   > No se usa Firebase Storage: desde el 3 de febrero de 2026 exige el plan de pago Blaze. Las imágenes se guardan comprimidas dentro de Firestore, así que todo funciona en el plan gratuito **Spark**.

### 1.2 Llaves locales
Copia `local.properties.example` como `local.properties` y completa los valores:

```properties
epayco.publicKey=TU_PUBLIC_KEY    # Dashboard ePayco > Integraciones > Llaves API
epayco.test=true                  # modo pruebas
admin.code=TV-ADMIN-2026          # código para registrarse como Administrador
```

### 1.3 Compilar y ejecutar
1. Abre la carpeta del proyecto con Android Studio (Koala o superior) y espera a que Gradle sincronice.
2. Conecta un **teléfono físico** con la depuración USB activada. La huella y el GPS se prueban en el dispositivo real.
3. Ejecuta **Run ▶**. El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

### 1.4 Primeros datos
1. Regístrate como **Administrador** con el código de `admin.code`.
2. Desde *Gestión de usuarios* crea un Vendedor y un Comprador (o regístralos desde la app).
3. Con el Vendedor publica productos con foto.
4. Con el Comprador agrega productos al carrito y paga con una tarjeta de prueba de ePayco. Por ejemplo, la tarjeta `4575 6231 8229 0326` con cualquier CVV y una fecha futura sale aprobada en modo pruebas. Revisa las tarjetas vigentes en la documentación de ePayco.

---

## 2. Arquitectura

La app usa una arquitectura en capas: **UI → Repositorios → Firebase / ePayco**.

```
com.tiendavirtual.app
├── data
│   ├── model/        User, Role, Product, CartItem, Order, OrderItem, OrderStatus
│   ├── repository/   AuthRepository, UserRepository, ProductRepository, CartRepository, OrderRepository
│   └── payment/      EpaycoService (validación de transacciones)
├── ui
│   ├── auth/         Splash, Login, Register, RecoverPassword
│   ├── admin/        Dashboard (drawer), Users, UserForm, Products, Sales
│   ├── seller/       SellerProducts, ProductForm, SellerOrders
│   ├── buyer/        BuyerHome, Catalog, ProductDetail, Cart, Checkout, Payment, BuyerOrders
│   └── common/       BaseActivity, Navigator, Adapters, ProfileActivity, OrderMapLauncher
└── util/             BiometricAuth, LocationHelper, ImageCompressor, Formatters, Session, AppException
```

- **Activities** solo manejan la vista. Toda lectura y escritura pasa por un **repositorio**.
- **Corrutinas:** `BaseActivity.launchUi` ejecuta cada operación ligada al ciclo de vida de la pantalla, muestra la barra de progreso y traduce los errores a mensajes en español (`AppException.toUserMessage`).
- **Session** mantiene en memoria el perfil autenticado para no consultar Firestore en cada pantalla.
- Las listas usan **RecyclerView** con adaptadores (`Adapters.kt`) en lugar de las tarjetas fijas de la entrega anterior.
- Los filtros y ordenamientos se hacen en memoria, así que **no se necesitan índices compuestos** en Firestore.

### 2.1 Modelo de datos (Firestore)

```
users/{uid}                 name, email, phone, role(ADMIN|SELLER|BUYER), active, storeName, address, city, createdAt
products/{productId}        name, description, price, stock, category, imageUrl (data URI JPEG base64), sellerId, sellerName, active, createdAt
carts/{uid}/items/{productId}   name, price, quantity, imageUrl, sellerId
orders/{orderId}            buyerId, buyerName, buyerEmail, items[], sellerIds[], total, address, city,
                            latitude, longitude, status, paymentRef, paymentTransactionId, paymentMessage, createdAt, paidAt
```

Las imágenes viven en el campo `imageUrl` del producto (máx. ~400 KB en base64, por debajo del límite de 1 MB por documento).

### 2.2 Flujo de compra y pago

```
Carrito ─► Checkout (dirección manual o GPS) ─► OrderRepository.createPending (valida stock)
        ─► PaymentActivity (WebView con checkout.js de ePayco)
        ─► ePayco redirige a RESPONSE_URL?ref_payco=…  (la app intercepta la URL)
        ─► EpaycoService.validate(ref) ─┬─ Aceptada  → confirmPayment: transacción que descuenta stock y marca PAID, se vacía el carrito
                                        ├─ Pendiente → guarda la referencia
                                        └─ Rechazada → REJECTED (queda en el historial)
Vendedor: PAID ─► SHIPPED ─► DELIVERED
```

### 2.3 Inicio de sesión con huella

1. Después del primer ingreso con contraseña, la app ofrece activar la huella. También se activa desde *Perfil*.
2. Se crea en el **Android Keystore** una llave AES/GCM con `setUserAuthenticationRequired(true)`.
3. `BiometricPrompt` desbloquea la llave (`CryptoObject`) y con ella se cifran correo y contraseña. En el teléfono solo queda el texto cifrado.
4. Para ingresar, la huella descifra las credenciales y se inicia sesión en Firebase.
5. Si se registra una huella nueva, la llave se invalida y se pide la contraseña otra vez.

---

## 3. Permisos

| Permiso | Uso |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Firebase, ePayco, imágenes |
| `USE_BIOMETRIC` / `USE_FINGERPRINT` | Ingreso con huella |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | Dirección de entrega (se pide al tocar "Usar mi ubicación actual") |
| `CAMERA` | Foto del producto (se pide al elegir "Tomar foto") |

---

## 4. Limitaciones conocidas

- **Confirmación del pago en el cliente:** la app valida la transacción con la API de ePayco y actualiza la orden desde el teléfono. En producción esto lo haría la URL de *confirmación* de ePayco en un servidor (por ejemplo, Cloud Functions), para que un cliente modificado no pueda marcar órdenes como pagadas.
- **Eliminar usuarios:** el Administrador elimina el perfil en Firestore y con eso bloquea el acceso. Borrar la cuenta de Authentication de otra persona requiere el Admin SDK en un servidor.
- **Código de administrador:** se valida en la app. Las reglas de Firestore no pueden verificarlo.

---

## 5. Repositorio

Los commits del historial siguen el orden de construcción del backend: configuración → modelos → repositorios → utilidades → pantallas por rol → reglas y documentación. Para publicarlo:

```bash
gh repo create tienda-virtual-android --public --source . --push
# o, con un repositorio ya creado en GitHub:
git remote add origin https://github.com/USUARIO/tienda-virtual-android.git
git push -u origin main
```
