function pemToDerBytes(publicKeyPem: string) {
  const normalized = publicKeyPem
    .replace(/-----BEGIN PUBLIC KEY-----/g, '')
    .replace(/-----END PUBLIC KEY-----/g, '')
    .replace(/\s+/g, '')

  const binary = atob(normalized)
  return Uint8Array.from(binary, (char) => char.charCodeAt(0))
}

function readLength(bytes: Uint8Array, offset: number) {
  const first = bytes[offset]
  if ((first & 0x80) === 0) {
    return {
      length: first,
      nextOffset: offset + 1,
    }
  }

  const size = first & 0x7f
  let length = 0
  for (let index = 0; index < size; index += 1) {
    length = (length << 8) | bytes[offset + 1 + index]
  }

  return {
    length,
    nextOffset: offset + 1 + size,
  }
}

function readNode(bytes: Uint8Array, offset: number) {
  const tag = bytes[offset]
  const { length, nextOffset } = readLength(bytes, offset + 1)
  const start = nextOffset
  const end = start + length

  return {
    tag,
    value: bytes.slice(start, end),
    nextOffset: end,
  }
}

function bytesToBigInt(bytes: Uint8Array) {
  const normalized = bytes[0] === 0 ? bytes.slice(1) : bytes
  const hex = Array.from(normalized, (value) => value.toString(16).padStart(2, '0')).join('')
  return hex.length === 0 ? 0n : BigInt(`0x${hex}`)
}

function parseRsaPublicKey(publicKeyPem: string) {
  const derBytes = pemToDerBytes(publicKeyPem)
  const sequenceNode = readNode(derBytes, 0)
  const bitStringNode = readNode(sequenceNode.value, readNode(sequenceNode.value, 0).nextOffset)
  const rsaSequence = readNode(bitStringNode.value.slice(1), 0)
  const modulusNode = readNode(rsaSequence.value, 0)
  const exponentNode = readNode(rsaSequence.value, modulusNode.nextOffset)

  return {
    modulus: bytesToBigInt(modulusNode.value),
    exponent: bytesToBigInt(exponentNode.value),
    keySize: modulusNode.value[0] === 0 ? modulusNode.value.length - 1 : modulusNode.value.length,
  }
}

function modPow(base: bigint, exponent: bigint, modulus: bigint) {
  let result = 1n
  let currentBase = base % modulus
  let currentExponent = exponent

  while (currentExponent > 0n) {
    if ((currentExponent & 1n) === 1n) {
      result = (result * currentBase) % modulus
    }
    currentBase = (currentBase * currentBase) % modulus
    currentExponent >>= 1n
  }

  return result
}

function bigintToBytes(value: bigint, size: number) {
  const bytes = new Uint8Array(size)
  let cursor = value

  for (let index = size - 1; index >= 0; index -= 1) {
    bytes[index] = Number(cursor & 0xffn)
    cursor >>= 8n
  }

  return bytes
}

function createPkcs1Block(payload: Uint8Array, blockSize: number) {
  if (payload.length > blockSize - 11) {
    throw new Error('密码长度超出 RSA 加密限制')
  }

  const paddingLength = blockSize - payload.length - 3
  const padding = new Uint8Array(paddingLength)

  for (let index = 0; index < padding.length; index += 1) {
    let value = 0
    while (value === 0) {
      const randomBytes = new Uint8Array(1)
      globalThis.crypto.getRandomValues(randomBytes)
      value = randomBytes[0]
    }
    padding[index] = value
  }

  const output = new Uint8Array(blockSize)
  output[0] = 0
  output[1] = 2
  output.set(padding, 2)
  output[2 + paddingLength] = 0
  output.set(payload, 3 + paddingLength)
  return output
}

function bytesToBase64(bytes: Uint8Array) {
  let binary = ''
  bytes.forEach((value) => {
    binary += String.fromCharCode(value)
  })
  return btoa(binary)
}

export async function encryptPassword(password: string, publicKeyPem: string) {
  const encoder = new TextEncoder()
  const payload = encoder.encode(password)
  const { modulus, exponent, keySize } = parseRsaPublicKey(publicKeyPem)
  const block = createPkcs1Block(payload, keySize)
  const encrypted = modPow(bytesToBigInt(block), exponent, modulus)
  return bytesToBase64(bigintToBytes(encrypted, keySize))
}
