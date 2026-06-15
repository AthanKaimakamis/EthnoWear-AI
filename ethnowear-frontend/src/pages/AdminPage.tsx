import { Box, Typography } from '@mui/material'

function AdminPage() {
    return (
        <Box sx={{ p: 4 }}>
            <Typography variant="h4" gutterBottom>
                Admin
            </Typography>
            <Typography color="text.secondary">
                Admin tools will be added here.
            </Typography>
        </Box>
    )
}

export default AdminPage
