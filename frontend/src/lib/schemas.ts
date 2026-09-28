import { z } from 'zod';

// Client-side mirrors of the server's Bean Validation rules. The server remains the authority;
// these exist so people see mistakes before a round trip.

export const PAN = /^[A-Z]{5}[0-9]{4}[A-Z]$/;
export const AADHAAR = /^[2-9][0-9]{11}$/;
export const PHONE = /^[6-9][0-9]{9}$/;
export const PINCODE = /^[1-9][0-9]{5}$/;
export const IFSC = /^[A-Z]{4}0[A-Z0-9]{6}$/;
export const ACCOUNT_NUMBER = /^[0-9]{9,18}$/;
export const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,64}$/;

const isAdult = (value: string) => {
  const dob = new Date(`${value}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return false;
  const eighteen = new Date(dob);
  eighteen.setFullYear(dob.getFullYear() + 18);
  return eighteen <= new Date();
};

export const customerIdentitySchema = z.object({
  fullName: z.string().trim().min(3, 'Enter your full name'),
  email: z.email('Enter a valid email address'),
  phone: z.string().regex(PHONE, 'Enter a 10-digit mobile number'),
  dateOfBirth: z.string().min(1, 'Enter your date of birth').refine(isAdult, 'You must be at least 18'),
  panNumber: z.string().trim().toUpperCase().regex(PAN, 'PAN looks like ABCPE1234F'),
  aadhaarNumber: z.string().regex(AADHAAR, 'Aadhaar is 12 digits and does not start with 0 or 1'),
  addressLine: z.string().trim().min(3, 'Enter your address'),
  city: z.string().trim().min(2, 'Enter your city'),
  state: z.string().trim().min(2, 'Enter your state'),
  pincode: z.string().regex(PINCODE, 'Enter a 6-digit PIN code'),
});

/** Amounts are entered in rupees with at most two decimals (paise). */
export const amountSchema = (min = 1) =>
  z.number({ error: 'Enter an amount' })
    .min(min, `The minimum is ₹${min.toLocaleString('en-IN')}`)
    .refine((value) => Math.round(value * 100) / 100 === value, 'Use at most two decimal places');
