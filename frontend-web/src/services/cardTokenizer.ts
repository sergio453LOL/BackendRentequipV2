/**
 * Sandbox version of Stripe.js tokenization: turns the card typed in the browser into a single-use token,
 * so the card number never travels to our backend. The test card numbers and the tokens they produce are
 * the ones Stripe publishes for test mode, which keeps the flow identical when a real Stripe key is added.
 */

export type CardBrand = 'visa' | 'mastercard' | 'amex' | 'unknown'

export interface CardDetails {
  number: string
  expiry: string
  cvc: string
  holderName: string
}

export interface TestCard {
  number: string
  label: string
}

export const TEST_CARDS: readonly TestCard[] = [
  { number: '4242 4242 4242 4242', label: 'Visa · pago aprobado' },
  { number: '5555 5555 5555 4444', label: 'Mastercard · pago aprobado' },
  { number: '4000 0000 0000 9995', label: 'Fondos insuficientes' },
  { number: '4000 0000 0000 0002', label: 'Tarjeta rechazada' },
]

const TOKENS_BY_CARD: Record<string, string> = {
  '4242424242424242': 'tok_visa',
  '5555555555554444': 'tok_mastercard',
  '4000000000000002': 'tok_chargeDeclined',
  '4000000000009995': 'tok_chargeDeclinedInsufficientFunds',
  '4000000000000069': 'tok_chargeDeclinedExpiredCard',
  '4000000000000127': 'tok_chargeDeclinedIncorrectCvc',
}

const TOKENIZATION_DELAY_MS = 600

export function digitsOnly(value: string): string {
  return value.replace(/\D/g, '')
}

export function detectCardBrand(cardNumber: string): CardBrand {
  const digits = digitsOnly(cardNumber)
  if (/^4/.test(digits)) return 'visa'
  if (/^(5[1-5]|2[2-7])/.test(digits)) return 'mastercard'
  if (/^3[47]/.test(digits)) return 'amex'
  return 'unknown'
}

/** Luhn checksum: catches typos in the card number before any request is made. */
export function passesLuhn(cardNumber: string): boolean {
  const digits = digitsOnly(cardNumber)
  if (digits.length < 13 || digits.length > 19) return false
  let sum = 0
  for (let index = 0; index < digits.length; index++) {
    let digit = Number(digits[digits.length - 1 - index])
    if (index % 2 === 1) {
      digit *= 2
      if (digit > 9) digit -= 9
    }
    sum += digit
  }
  return sum % 10 === 0
}

/** `MM/AA` that is a real month and is not in the past. */
export function isValidExpiry(expiry: string, now: Date = new Date()): boolean {
  const match = /^(\d{2})\/(\d{2})$/.exec(expiry)
  if (!match) return false
  const month = Number(match[1])
  const year = 2000 + Number(match[2])
  if (month < 1 || month > 12) return false
  const endOfMonth = new Date(year, month, 0, 23, 59, 59)
  return endOfMonth >= now
}

export function formatCardNumber(value: string): string {
  return digitsOnly(value).slice(0, 16).replace(/(\d{4})(?=\d)/g, '$1 ')
}

export function formatExpiry(value: string): string {
  const digits = digitsOnly(value).slice(0, 4)
  return digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits
}

/** Any other valid card is treated like Stripe's generic successful test card. */
export async function createCardToken(card: CardDetails): Promise<string> {
  await new Promise((resolve) => setTimeout(resolve, TOKENIZATION_DELAY_MS))
  return TOKENS_BY_CARD[digitsOnly(card.number)] ?? 'tok_visa'
}
