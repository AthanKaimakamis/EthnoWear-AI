import i18n from '../../app/i18n'

i18n.addResourceBundle('bg', 'publicAuth', {
    googleAccount: 'Личен профил · Google', managerLogin: 'Вход за управление',
    signedIn: 'Влезли сте като {{name}}.',
    account: 'Личен профил', login: 'Вход с Google', logout: 'Изход от личния профил', guest: 'Продължи като гост',
    loading: 'Зареждане', retry: 'Опитай отново', restart: 'Входът изтече. Опитайте отново с Google.',
    disabled: 'Този профил не е достъпен.', denied: 'Заявката не беше приета. Опитайте да влезете отново.',
    rateLimited: 'Твърде много опити. Изчакайте, преди да опитате отново.',
    unavailable: 'Входът с Google временно не е достъпен. Можете да продължите като гост.',
})
i18n.addResourceBundle('en', 'publicAuth', {
    googleAccount: 'Personal account · Google', managerLogin: 'Log in as manager',
    signedIn: 'Signed in as {{name}}.',
    account: 'Personal account', login: 'Sign in with Google', logout: 'Sign out of personal account', guest: 'Continue as guest',
    loading: 'Loading', retry: 'Try again', restart: 'Sign-in expired. Please try Google sign-in again.',
    disabled: 'This account is unavailable.', denied: 'The request was not accepted. Please sign in again.',
    rateLimited: 'Too many attempts. Please wait before trying again.',
    unavailable: 'Google sign-in is temporarily unavailable. You can continue as a guest.',
})
