import { createTheme } from '@mui/material/styles'

export const lightTheme = createTheme({
    palette: {
        mode: 'light',

        primary: {
            main: '#8B1028',
            dark: '#65091C',
            light: '#B43B50',
            contrastText: '#FFFFFF'
        },

        secondary: {
            main: '#315B4A',
            contrastText: '#FFFFFF'
        },

        background : {
            default: '#F5F6F4',
            paper: '#FFFFFF',
        },

        text: {
            primary: '#171817',
            secondary: '#5F625F'
        },

        divider: '#D3D7D3',
    },

    shape: {
        borderRadius: 4,
    },

    typography: {
        fontFamily: 'Roboto, system-ui, sans-serif',
        h1: {
            fontWeight: 700,
        },
        h2:{
            fontWeight: 700,
        },
        h3: {
            fontWeight: 600,
        },
        h4: {
            fontWeight: 700,
        },
        button: {
            textTransform: 'uppercase',
            fontWeight: '600',
            fontSize: '0.75rem',
        },
    },

    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    borderRadius: 3,
                },
            },
        },

        MuiPaper: {
            styleOverrides: {
                root: {
                    backgroundImage: 'none'
                }
            }
        },

        MuiCard: {
            styleOverrides: {
                root: {
                    border: '1px solid #D3D7D3',
                    boxShadow: '0 2px 7px rgba(24, 28, 24, 0.10)',
                },
            },
        },

        MuiOutlinedInput: {
            styleOverrides: {
                root: {
                    backgroundColor: '#FFFFFF',
                    '&.Mui-disabled': {
                        backgroundColor: '#ECEFEC',
                    },
                    '& .MuiOutlinedInput-notchedOutline': {
                        borderColor: '#BFC5BF',
                    },
                    '&:hover .MuiOutlinedInput-notchedOutline': {
                        borderColor: '#7D847F',
                    },
                },
            },
        },

        MuiChip: {
            styleOverrides: {
                root: {
                    borderRadius: 3,
                    height: 22,
                    fontSize: '0.68rem',
                    fontWeight: 600,
                    textTransform: 'uppercase',
                },
            },
        },

        MuiCheckbox: {
            styleOverrides: {
                root: {
                    color: '#7D847F',
                    '&.Mui-checked': {
                        color: '#8B1028',
                    },
                },
            },
        },
    },
})

export const darkTheme = createTheme({
    palette: {
        mode: 'dark',

        primary: {
            main: '#D45A68',
            contrastText: '#1B0E11',
        },

        secondary: {
            main: '#7AB6C9',
            contrastText: '#07191F',
        },

        background: {
            default: '#141211',
            paper: '#1F1B18',
        },

        text: {
            primary: '#F4EEE8',
            secondary: '#C9BDB4',
        },

        divider: '#3A312C',
    },

    shape: {
        borderRadius: 8,
    },

    typography: {
        fontFamily: 'Roboto, system-ui, sans-serif',

        h1: {
            fontWeight: 700,
        },

        h2: {
            fontWeight: 700,
        },

        h3: {
            fontWeight: 600,
        },

        button: {
            textTransform: 'none',
            fontWeight: 600,
        },
    },

    components: {
        MuiButton: {
            styleOverrides: {
                root: {
                    borderRadius: 8,
                },
            },
        },

        MuiPaper: {
            styleOverrides: {
                root: {
                    backgroundImage: 'none',
                },
            },
        },
    },
})
