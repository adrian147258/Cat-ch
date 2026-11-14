import { scrypt, randomBytes, timingSafeEqual } from 'node:crypto';
const derive=(password,salt)=>new Promise((resolve,reject)=>scrypt(password.normalize('NFKC'),salt,64,{N:16384,r:16,p:1,maxmem:67108864},(e,k)=>e?reject(e):resolve(k)));
const passwordCrypto={hash:async password=>{const salt=randomBytes(16).toString('hex');return salt+':'+(await derive(password,salt)).toString('hex');},verify:async({hash,password})=>{const [salt,key]=hash.split(':');if(!salt||!key)return false;const expected=Buffer.from(key,'hex');const actual=await derive(password,salt);return expected.length===actual.length&&timingSafeEqual(expected,actual);}};
import { betterAuth } from 'better-auth';
import { bearer, emailOTP } from 'better-auth/plugins';

export function makeAuth(env, sendOverride) {
  if (!env.BETTER_AUTH_SECRET || env.BETTER_AUTH_SECRET.length < 32) throw new Error('Auth secret missing');
  const auth=betterAuth({
    appName:'Cat-ch', baseURL:env.AUTH_BASE_URL,
    secret:env.BETTER_AUTH_SECRET, database:env.DB,
    emailAndPassword:{enabled:true,password:passwordCrypto,onExistingUserSignUp:async({user})=>{if(!user.emailVerified)await auth.api.sendVerificationOTP({body:{email:user.email,type:"email-verification"}});},requireEmailVerification:true,minPasswordLength:10,maxPasswordLength:128},
    emailVerification:{sendOnSignUp:true,autoSignInAfterVerification:true},
    advanced:{ipAddress:{ipAddressHeaders:['cf-connecting-ip']}},
    rateLimit:{enabled:true,storage:'database',window:60,max:30,customRules:{'/sign-in/email':{window:60,max:5},'/sign-up/email':{window:3600,max:5},'/email-otp/send-verification-otp':{window:60,max:3},'/sign-in/email-otp':{window:60,max:5}}},
    plugins:[bearer(),emailOTP({otpLength:6,expiresIn:600,allowedAttempts:5,storeOTP:'hashed',disableSignUp:true,overrideDefaultEmailVerification:true,
      async sendVerificationOTP({email,otp,type}){
        if(sendOverride)return sendOverride({email,otp,type});
        if(!env.BREVO_API_KEY)throw new Error('Email service not configured');
        const response=await fetch('https://api.brevo.com/v3/smtp/email',{method:'POST',headers:{'accept':'application/json','content-type':'application/json','api-key':env.BREVO_API_KEY},body:JSON.stringify({sender:{name:'猫猫社',email:env.BREVO_SENDER_EMAIL},to:[{email}],subject:'猫猫社邮箱验证码',textContent:`您的猫猫社验证码是：${otp}。验证码 10 分钟内有效。如非本人操作，请忽略此邮件。`})});
        if(!response.ok){console.error(JSON.stringify({event:'email_send_failed',status:response.status}));throw new Error('Email delivery unavailable');}
      }
    })]
  });
  return auth;
}
